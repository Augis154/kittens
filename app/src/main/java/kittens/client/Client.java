package kittens.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import kittens.common.GameConfig;
import kittens.common.map.Tile;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.sim.PlayerMotion;

/**
 * Rendering + input, with client-side prediction for the local player: input is applied to a local
 * copy of the movement model immediately (no round-trip felt), then each snapshot re-anchors that
 * prediction to the authoritative state and replays the inputs the server hasn't acknowledged yet.
 * Remote players are shown with plain snapshot interpolation.
 */
public final class Client extends JPanel {
  private static final Color FLOOR = new Color(30, 30, 36);
  private static final Color FLOOR_GRID = new Color(40, 40, 48);
  private static final Color WALL = new Color(70, 74, 92);
  private static final Color WALL_TOP = new Color(96, 100, 122);
  private static final Color SPAWN_TILE = new Color(38, 52, 44);

  private static final int FPS = 60;
  private static final int INPUT_HZ = GameConfig.TICK_HZ;
  private static final double INPUT_DT = 1.0 / GameConfig.TICK_HZ;
  /** Per-frame easing of remote entities toward their snapshot position. */
  private static final float REMOTE_SMOOTHING = 0.30f;
  /** Correction easing when the local prediction is only slightly off. */
  private static final float RECONCILE_SMOOTHING = 0.25f;
  /** Prediction error (px) above which we hard-snap instead of easing. */
  private static final float RECONCILE_SNAP = 64f;
  private static final int RELEASE_GRACE_MS = 45;

  private record Pending(long seq, float moveX, float moveY) {}

  private final TileMap map = TileMap.fromResource(GameConfig.MAP_RESOURCE, GameConfig.TILE);
  private final AssetManager assets = new AssetManager();
  private final GameClient client;

  // Local-player prediction.
  private Vec2 predicted;
  private final Deque<Pending> unacked = new ArrayDeque<>();
  private long lastSnapshotVersion = -1;

  // Remote players: interpolated on-screen position per id -> {x, y}.
  private final Map<Integer, float[]> remotePos = new HashMap<>();

  // Input state (EDT only).
  private final Set<Integer> held = new HashSet<>();
  private final Map<Integer, Timer> pendingRelease = new HashMap<>();
  private float moveX;
  private float moveY;
  private int mouseX;
  private int mouseY;
  private boolean firing;
  private long lastFrameNanos;

  public Client(GameClient client) {
    this.client = client;
    setPreferredSize(new Dimension((int) map.pixelWidth(), (int) map.pixelHeight()));
    setBackground(FLOOR);
    setFocusable(true);
    mouseX = (int) map.pixelWidth() / 2;
    mouseY = (int) map.pixelHeight() / 2;
    installInput();

    new Timer(1000 / INPUT_HZ, e -> sendInputTick()).start();
    new Timer(1000 / FPS, e -> {
      frame();
      repaint();
    }).start();
  }

  // ---- input ----------------------------------------------------------------

  private void installInput() {
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        Timer grace = pendingRelease.remove(e.getKeyCode());
        if (grace != null) {
          grace.stop();
        }
        if (held.add(e.getKeyCode())) {
          recomputeDirection();
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        // X11 autorepeat emits release+press pairs while a key is held; defer the release and
        // cancel it if the matching press arrives, so movement doesn't stutter.
        int code = e.getKeyCode();
        Timer grace = new Timer(RELEASE_GRACE_MS, ev -> {
          pendingRelease.remove(code);
          if (held.remove(code)) {
            recomputeDirection();
          }
        });
        grace.setRepeats(false);
        pendingRelease.put(code, grace);
        grace.start();
      }
    });

    MouseMotionAdapter mouse = new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
      }
    };
    addMouseMotionListener(mouse);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          firing = true;
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          firing = false;
        }
      }
    });
  }

  /** Angle (radians, screen space) from the local player toward the cursor. */
  private float aimAngle() {
    Vec2 p = predicted;
    if (p == null) {
      return 0f;
    }
    return (float) Math.atan2(mouseY - p.y, mouseX - p.x);
  }

  private boolean down(int... codes) {
    for (int c : codes) {
      if (held.contains(c)) {
        return true;
      }
    }
    return false;
  }

  private void recomputeDirection() {
    moveX = (down(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1f : 0f)
        - (down(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1f : 0f);
    moveY = (down(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1f : 0f)
        - (down(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1f : 0f);
  }

  /** Fixed-rate: hand the current intent to the server and remember it for reconciliation. */
  private void sendInputTick() {
    if (client.myPlayerId() < 0) {
      return;
    }
    InputCommand cmd = client.sendInput(moveX, moveY, aimAngle(), firing);
    unacked.addLast(new Pending(cmd.seq(), cmd.moveX(), cmd.moveY()));
    while (unacked.size() > 4 * INPUT_HZ) { // ~4s safety cap
      unacked.pollFirst();
    }
  }

  // ---- per-frame simulation ------------------------------------------------

  private void frame() {
    long now = System.nanoTime();
    double dt = lastFrameNanos == 0 ? 0 : Math.min(0.1, (now - lastFrameNanos) / 1_000_000_000.0);
    lastFrameNanos = now;

    reconcile();

    if (predicted != null && dt > 0) {
      predicted =
          PlayerMotion.step(map, predicted, moveX, moveY, GameConfig.PLAYER_SPEED, dt);
    }

    interpolateRemotes();
  }

  /** On each new snapshot, re-anchor the prediction to authority + replay un-acked inputs. */
  private void reconcile() {
    long version = client.snapshotVersion();
    if (version == lastSnapshotVersion) {
      return;
    }
    lastSnapshotVersion = version;

    int me = client.myPlayerId();
    EntityState mine = me < 0 ? null : client.entities().get(me);
    if (mine == null) {
      return;
    }

    long ack = client.ackSeq();
    while (!unacked.isEmpty() && unacked.peekFirst().seq() <= ack) {
      unacked.pollFirst();
    }

    Vec2 target = Vec2.of(mine.x(), mine.y());
    for (Pending p : unacked) {
      target = PlayerMotion.step(
          map, target, p.moveX(), p.moveY(), GameConfig.PLAYER_SPEED, INPUT_DT);
    }

    if (predicted == null || predicted.distance(target) > RECONCILE_SNAP) {
      predicted = target; // first snapshot, or a desync worth snapping
    } else {
      predicted = predicted.add(target.sub(predicted).scale(RECONCILE_SMOOTHING));
    }
  }

  private void interpolateRemotes() {
    int me = client.myPlayerId();
    Map<Integer, EntityState> live = client.entities();
    remotePos.keySet().removeIf(id -> id == me || !live.containsKey(id));
    for (EntityState e : live.values()) {
      if (e.id() == me || !"cat".equals(e.kind())) {
        continue; // bullets are drawn at their raw position, not interpolated
      }
      float[] rp = remotePos.get(e.id());
      if (rp == null) {
        remotePos.put(e.id(), new float[] {e.x(), e.y()});
      } else {
        rp[0] += (e.x() - rp[0]) * REMOTE_SMOOTHING;
        rp[1] += (e.y() - rp[1]) * REMOTE_SMOOTHING;
      }
    }
  }

  // ---- rendering ----------------------------------------------------------

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

    drawMap(g2);
    drawEntities(g2);
    drawHud(g2);
  }

  private void drawMap(Graphics2D g) {
    int t = GameConfig.TILE;
    for (int row = 0; row < map.height(); row++) {
      for (int col = 0; col < map.width(); col++) {
        int px = col * t;
        int py = row * t;
        switch (map.tileAt(col, row)) {
          case WALL -> {
            g.setColor(WALL);
            g.fillRect(px, py, t, t);
            g.setColor(WALL_TOP);
            g.fillRect(px, py, t, 4);
          }
          case SPAWN -> {
            g.setColor(SPAWN_TILE);
            g.fillRect(px, py, t, t);
            g.setColor(FLOOR_GRID);
            g.drawRect(px, py, t - 1, t - 1);
          }
          case FLOOR -> {
            g.setColor(FLOOR_GRID);
            g.drawRect(px, py, t - 1, t - 1);
          }
        }
      }
    }
  }

  private void drawEntities(Graphics2D g) {
    int me = client.myPlayerId();

    for (EntityState e : client.entities().values()) {
      if ("bullet".equals(e.kind())) {
        drawBullet(g, e);
      }
    }

    for (Map.Entry<Integer, float[]> entry : remotePos.entrySet()) {
      int id = entry.getKey();
      drawKitten(g, id, entry.getValue()[0], entry.getValue()[1], angleOf(id), hpOf(id), false);
    }
    if (predicted != null) {
      drawKitten(g, me, predicted.x, predicted.y, aimAngle(), hpOf(me), true);
    } else {
      EntityState mine = me < 0 ? null : client.entities().get(me);
      if (mine != null) {
        drawKitten(g, me, mine.x(), mine.y(), mine.angle(), mine.hp(), true);
      }
    }
  }

  private float angleOf(int id) {
    EntityState e = client.entities().get(id);
    return e == null ? 0f : e.angle();
  }

  private float hpOf(int id) {
    EntityState e = client.entities().get(id);
    return e == null ? (float) GameConfig.PLAYER_MAX_HEALTH : e.hp();
  }

  private void drawBullet(Graphics2D g, EntityState b) {
    int tailX = Math.round(b.x() - (float) Math.cos(b.angle()) * 9f);
    int tailY = Math.round(b.y() - (float) Math.sin(b.angle()) * 9f);
    Stroke saved = g.getStroke();
    g.setColor(new Color(255, 224, 130));
    g.setStroke(new BasicStroke(2f));
    g.drawLine(tailX, tailY, Math.round(b.x()), Math.round(b.y()));
    g.fillOval(Math.round(b.x()) - 2, Math.round(b.y()) - 2, 4, 4);
    g.setStroke(saved);
  }

  private void drawKitten(
      Graphics2D g, int id, float cx, float cy, float angle, float hp, boolean self) {
    int t = GameConfig.TILE;
    int x = Math.round(cx - t / 2f);
    int y = Math.round(cy - t / 2f);
    boolean facingLeft = Math.cos(angle) < 0;
    boolean dead = hp <= 0f;

    if (self && !dead) {
      g.setColor(new Color(120, 210, 255, 90));
      g.drawLine(Math.round(cx), Math.round(cy), mouseX, mouseY);
      g.setColor(new Color(120, 210, 255));
      g.drawOval(x - 2, y - 2, t + 3, t + 3);
    }

    if (dead) {
      // Downed: a faint marker where the kitten will respawn from view soon.
      g.setColor(new Color(180, 90, 90, 120));
      g.drawLine(x + 4, y + 4, x + t - 4, y + t - 4);
      g.drawLine(x + t - 4, y + 4, x + 4, y + t - 4);
      g.setColor(new Color(255, 255, 255, 120));
      g.drawString("P" + id, x, y - 4);
      return;
    }

    // Kitten, flipped horizontally to face the aim direction.
    BufferedImage kitten = assets.kitten(GameConfig.kittenSprite(id));
    if (facingLeft) {
      g.drawImage(kitten, x + t, y, -t, t, null);
    } else {
      g.drawImage(kitten, x, y, t, t, null);
    }

    // Weapon, rotated around the player toward the aim angle.
    int w = 22;
    AffineTransform saved = g.getTransform();
    g.translate(cx, cy);
    g.rotate(angle);
    if (facingLeft) {
      g.scale(1, -1); // keep the gun upright when aiming left
    }
    g.drawImage(assets.weapon("pistol"), 4, -w / 2, w, w, null);
    g.setTransform(saved);

    // Health bar.
    float frac = Math.max(0f, Math.min(1f, hp / (float) GameConfig.PLAYER_MAX_HEALTH));
    if (frac < 1f) {
      int bw = t;
      int by = y - 8;
      g.setColor(new Color(0, 0, 0, 140));
      g.fillRect(x, by, bw, 3);
      g.setColor(frac > 0.4f ? new Color(120, 210, 120) : new Color(220, 110, 90));
      g.fillRect(x, by, Math.round(bw * frac), 3);
    }

    g.setColor(Color.WHITE);
    g.drawString("P" + id, x, y - 12);
  }

  private void drawHud(Graphics2D g) {
    g.setColor(new Color(255, 255, 255, 180));
    int me = client.myPlayerId();
    String who = me < 0 ? "connecting…" : "you are P" + me;
    g.drawString(who + "   —   WASD / arrows move · mouse aim · click to fire",
        8, (int) map.pixelHeight() - 8);
  }

  public static void main(String[] args) throws IOException {
    String host = args.length > 0 ? args[0] : "localhost";
    GameClient client = new GameClient(host, GameConfig.PORT);
    client.start();

    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame("Kittens");
      frame.add(new Client(client));
      frame.pack();
      frame.setResizable(false);
      frame.setLocationRelativeTo(null);
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setVisible(true);
    });
  }
}
