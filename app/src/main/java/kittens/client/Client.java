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
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.sim.PlayerMotion;
import kittens.common.weapon.Weapon;

/**
 * Rendering + input, with client-side prediction for the local player: input is applied to a local
 * copy of the movement model immediately (no round-trip felt), then each snapshot re-anchors that
 * prediction to the authoritative state and replays the inputs the server hasn't acknowledged yet.
 * Remote players, computer-controlled enemies, and projectiles are smoothly interpolated/extrapolated.
 */
public final class Client extends JPanel {
  private static final Color FLOOR = new Color(30, 30, 36);
  private static final Color FLOOR_GRID = new Color(40, 40, 48);
  private static final Color WALL = new Color(70, 74, 92);
  private static final Color WALL_TOP = new Color(96, 100, 122);
  private static final Color SPAWN_TILE = new Color(38, 52, 44);

  private static final int FPS = 180;
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

  // Remote entities (players & enemies): interpolated on-screen position per id -> {x, y}.
  private final Map<Integer, float[]> remotePos = new HashMap<>();

  // Client-simulated projectiles: id -> {x, y, angle, vx, vy, weaponId} for smooth 60 FPS flight.
  private final Map<Integer, float[]> clientBullets = new HashMap<>();

  // Input state (EDT only).
  private final Set<Integer> held = new HashSet<>();
  private final Map<Integer, Timer> pendingRelease = new HashMap<>();
  private float moveX;
  private float moveY;
  private int mouseX;
  private int mouseY;
  private boolean firing;
  private Weapon selectedWeapon = Weapon.PISTOL;
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
    new Timer(
            1000 / FPS,
            e -> {
              frame();
              repaint();
            })
        .start();
  }

  // ---- input ----------------------------------------------------------------

  private void installInput() {
    addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            int code = e.getKeyCode();
            if (code >= KeyEvent.VK_1 && code < KeyEvent.VK_1 + Weapon.count()) {
              selectedWeapon = Weapon.byId(code - KeyEvent.VK_1);
              return;
            }
            Timer grace = pendingRelease.remove(code);
            if (grace != null) {
              grace.stop();
            }
            if (held.add(code)) {
              recomputeDirection();
            }
          }

          @Override
          public void keyReleased(KeyEvent e) {
            // X11 autorepeat emits release+press pairs while a key is held; defer the release and
            // cancel it if the matching press arrives, so movement doesn't stutter.
            int code = e.getKeyCode();
            Timer grace =
                new Timer(
                    RELEASE_GRACE_MS,
                    ev -> {
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

    MouseMotionAdapter mouse =
        new MouseMotionAdapter() {
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

    addMouseListener(
        new MouseAdapter() {
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
    moveX =
        (down(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1f : 0f)
            - (down(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1f : 0f);
    moveY =
        (down(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1f : 0f)
            - (down(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1f : 0f);
  }

  /** Fixed-rate: hand the current intent to the server and remember it for reconciliation. */
  private void sendInputTick() {
    if (client.myPlayerId() < 0) {
      return;
    }
    // A downed player can still aim, but sends no movement and records nothing to replay.
    if (localDead()) {
      client.sendInput(0f, 0f, aimAngle(), false, selectedWeapon.id());
      unacked.clear();
      return;
    }
    InputCommand cmd =
        client.sendInput(moveX, moveY, aimAngle(), firing, selectedWeapon.id());
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

    // A downed player can't move — don't predict past the authoritative (frozen) position.
    if (predicted != null && dt > 0 && !localDead()) {
      predicted = PlayerMotion.step(map, predicted, moveX, moveY, GameConfig.PLAYER_SPEED, dt);
    }

    interpolateRemotes();
    updateBullets(dt);
  }

  private boolean localDead() {
    int me = client.myPlayerId();
    return me >= 0 && hpOf(me) <= 0f;
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

    // Downed: pin exactly to the server's (frozen) position — no input replay, nothing to smooth.
    if (mine.hp() <= 0f) {
      unacked.clear();
      predicted = Vec2.of(mine.x(), mine.y());
      return;
    }

    long ack = client.ackSeq();
    while (!unacked.isEmpty() && unacked.peekFirst().seq() <= ack) {
      unacked.pollFirst();
    }

    Vec2 target = Vec2.of(mine.x(), mine.y());
    for (Pending p : unacked) {
      target =
          PlayerMotion.step(map, target, p.moveX(), p.moveY(), GameConfig.PLAYER_SPEED, INPUT_DT);
    }

    if (predicted == null || mine.hp() <= 0f || predicted.distance(target) > RECONCILE_SNAP) {
      predicted = target; // first snapshot, dead (frozen), or a desync worth snapping
    } else {
      predicted = predicted.add(target.sub(predicted).scale(RECONCILE_SMOOTHING));
    }
  }

  private void interpolateRemotes() {
    int me = client.myPlayerId();
    Map<Integer, EntityState> live = client.entities();
    remotePos.keySet().removeIf(id -> id == me || !live.containsKey(id));
    for (EntityState e : live.values()) {
      if (e.id() == me || "bullet".equals(e.kind()) || "boom".equals(e.kind())) {
        continue; // bullets are dead-reckoned in updateBullets(), booms are drawn raw
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

  /** Advances bullets smoothly each client frame (60 FPS) and reconciles with server snapshots. */
  private void updateBullets(double dt) {
    Map<Integer, EntityState> live = client.entities();

    // 1. Remove dead bullets no longer present in server snapshots
    clientBullets.keySet().removeIf(id -> {
      EntityState e = live.get(id);
      return e == null || !"bullet".equals(e.kind());
    });

    // 2. Synchronize new/existing bullets with server snapshot authority
    for (EntityState e : live.values()) {
      if (!"bullet".equals(e.kind())) {
        continue;
      }
      Weapon weapon = Weapon.byId(e.weaponId());
      float speed = weapon.projectileSpeed;
      float vx = (float) Math.cos(e.angle()) * speed;
      float vy = (float) Math.sin(e.angle()) * speed;

      float[] b = clientBullets.get(e.id());
      if (b == null) {
        // [x, y, angle, vx, vy, weaponId]
        clientBullets.put(
            e.id(), new float[] {e.x(), e.y(), e.angle(), vx, vy, e.weaponId()});
      } else {
        // Soft reconcile position toward authoritative server snapshot
        float dx = e.x() - b[0];
        float dy = e.y() - b[1];
        float distSq = dx * dx + dy * dy;
        if (distSq > 48f * 48f) {
          b[0] = e.x();
          b[1] = e.y();
        } else {
          b[0] += dx * 0.25f;
          b[1] += dy * 0.25f;
        }
        b[2] = e.angle();
        b[3] = vx;
        b[4] = vy;
        b[5] = e.weaponId();
      }
    }

    // 3. Extrapolate position forward for this client frame
    if (dt > 0) {
      for (float[] b : clientBullets.values()) {
        b[0] += b[3] * (float) dt;
        b[1] += b[4] * (float) dt;
      }
    }
  }

  // ---- rendering ----------------------------------------------------------

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

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

    // 1. Bullets (smooth 60 FPS client prediction / extrapolation)
    for (float[] b : clientBullets.values()) {
      drawBullet(g, b[0], b[1], b[2], (int) b[5]);
    }

    // 2. Enemies
    for (EntityState e : client.entities().values()) {
      if ("rat".equals(e.kind()) || "mouse".equals(e.kind())) {
        float[] pos = remotePos.get(e.id());
        float x = pos != null ? pos[0] : e.x();
        float y = pos != null ? pos[1] : e.y();
        drawEnemy(g, e, x, y);
      }
    }

    // 3. Remote Players
    for (Map.Entry<Integer, float[]> entry : remotePos.entrySet()) {
      int id = entry.getKey();
      EntityState e = client.entities().get(id);
      if (e != null && "cat".equals(e.kind())) {
        drawKitten(
            g,
            id,
            entry.getValue()[0],
            entry.getValue()[1],
            angleOf(id),
            hpOf(id),
            weaponOf(id),
            false);
      }
    }

    // 4. Local Player
    if (predicted != null) {
      drawKitten(g, me, predicted.x, predicted.y, aimAngle(), hpOf(me), selectedWeapon, true);
    } else {
      EntityState mine = me < 0 ? null : client.entities().get(me);
      if (mine != null) {
        drawKitten(
            g,
            me,
            mine.x(),
            mine.y(),
            mine.angle(),
            mine.hp(),
            Weapon.byId(mine.weaponId()),
            true);
      }
    }

    // 5. Explosions (on top)
    for (EntityState e : client.entities().values()) {
      if ("boom".equals(e.kind())) {
        drawBoom(g, e);
      }
    }
  }

  private void drawBoom(Graphics2D g, EntityState e) {
    float r = e.angle();          // current expanding radius
    float maxR = e.hp();          // final radius
    float progress = maxR > 0f ? r / maxR : 1f;
    int fade = Math.max(0, Math.round(150 * (1f - progress)));
    int cx = Math.round(e.x());
    int cy = Math.round(e.y());
    int ir = Math.round(r);
    g.setColor(new Color(255, 150, 60, Math.round(fade * 0.6f)));
    g.fillOval(cx - ir, cy - ir, ir * 2, ir * 2);
    Stroke saved = g.getStroke();
    g.setStroke(new BasicStroke(3f));
    g.setColor(new Color(255, 224, 150, fade));
    g.drawOval(cx - ir, cy - ir, ir * 2, ir * 2);
    g.setStroke(saved);
  }

  private float angleOf(int id) {
    EntityState e = client.entities().get(id);
    return e == null ? 0f : e.angle();
  }

  private float hpOf(int id) {
    EntityState e = client.entities().get(id);
    return e == null ? (float) GameConfig.PLAYER_MAX_HEALTH : e.hp();
  }

  private Weapon weaponOf(int id) {
    EntityState e = client.entities().get(id);
    return Weapon.byId(e == null ? 0 : e.weaponId());
  }

  private void drawBullet(Graphics2D g, float bx, float by, float angle, int weaponId) {
    boolean rocket = weaponId == Weapon.BAZOOKA.id();
    float tail = rocket ? 14f : 9f;
    int tailX = Math.round(bx - (float) Math.cos(angle) * tail);
    int tailY = Math.round(by - (float) Math.sin(angle) * tail);
    Stroke saved = g.getStroke();
    g.setColor(rocket ? new Color(255, 150, 70) : new Color(255, 224, 130));
    g.setStroke(new BasicStroke(rocket ? 4f : 2f));
    g.drawLine(tailX, tailY, Math.round(bx), Math.round(by));
    int r = rocket ? 4 : 2;
    g.fillOval(Math.round(bx) - r, Math.round(by) - r, r * 2, r * 2);
    g.setStroke(saved);
  }

  private void drawEnemy(Graphics2D g, EntityState e, float cx, float cy) {
    int t = GameConfig.TILE;
    int x = Math.round(cx - t / 2f);
    int y = Math.round(cy - t / 2f);
    boolean facingLeft = Math.cos(e.angle()) < 0;

    BufferedImage img = assets.enemy(e.kind());
    if (img != null) {
      if (facingLeft) {
        g.drawImage(img, x + t, y, -t, t, null);
      } else {
        g.drawImage(img, x, y, t, t, null);
      }
    }

    // Health bar above enemy
    double maxHp =
        "mouse".equals(e.kind()) ? GameConfig.MOUSE_MAX_HEALTH : GameConfig.RAT_MAX_HEALTH;
    float frac = Math.clamp(e.hp() / (float) maxHp, 0f, 1f);
    if (frac < 1f && frac > 0f) {
      int bw = Math.round(t * 0.75f);
      int bx = Math.round(cx - bw / 2f);
      int by = y - 5;
      g.setColor(new Color(0, 0, 0, 160));
      g.fillRect(bx, by, bw, 3);
      g.setColor(new Color(230, 70, 70));
      g.fillRect(bx, by, Math.round(bw * frac), 3);
    }
  }

  private void drawKitten(
      Graphics2D g,
      int id,
      float cx,
      float cy,
      float angle,
      float hp,
      Weapon weapon,
      boolean self) {
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
    g.drawImage(assets.weapon(weapon.sprite), 4, -w / 2, w, w, null);
    g.setTransform(saved);

    // Health bar.
    float frac = Math.clamp(hp / (float) GameConfig.PLAYER_MAX_HEALTH, 0f, 1f);
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
    int me = client.myPlayerId();
    int baseY = (int) map.pixelHeight() - 10;

    long enemyCount =
        client.entities().values().stream()
            .filter(e -> "rat".equals(e.kind()) || "mouse".equals(e.kind()))
            .count();
    g.setColor(new Color(255, 255, 255, 150));
    String who = me < 0 ? "connecting…" : "P" + me;
    g.drawString(
        who
            + "  ·  enemies: "
            + enemyCount
            + "  ·  WASD move · mouse aim · click fire · 1-4 weapon",
        8,
        baseY - 18);

    // Weapon selector.
    int x = 8;
    for (Weapon wpn : Weapon.values()) {
      boolean active = wpn == selectedWeapon;
      String label = (wpn.id() + 1) + " " + wpn.displayName;
      int wpx = g.getFontMetrics().stringWidth(label) + 12;
      if (active) {
        g.setColor(new Color(120, 210, 255, 60));
        g.fillRect(x, baseY - 12, wpx, 16);
      }
      g.setColor(active ? Color.WHITE : new Color(255, 255, 255, 110));
      g.drawString(label, x + 6, baseY);
      x += wpx + 4;
    }
  }

  public static void main(String[] args) throws IOException {
    String host = args.length > 0 ? args[0] : "localhost";
    GameClient client = new GameClient(host, GameConfig.PORT);
    client.start();

    SwingUtilities.invokeLater(
        () -> {
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
