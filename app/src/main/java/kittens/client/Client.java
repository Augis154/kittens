package kittens.client;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
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
 * The game window: input, client-side prediction for the local player, and the frame loop that
 * drives {@link Renderer} and {@link Hud}.
 *
 * <p>Input is applied to a local copy of the movement model immediately (no round-trip felt), then
 * each snapshot re-anchors that prediction to the authoritative state and replays the inputs the
 * server hasn't acknowledged yet. Everything the local player does <em>not</em> control is smoothed
 * by {@link WorldView} instead.
 */
public final class Client extends JPanel {
  private static final int FPS = 180;
  private static final int INPUT_HZ = GameConfig.TICK_HZ;
  private static final double INPUT_DT = 1.0 / GameConfig.TICK_HZ;
  /** Correction easing when the local prediction is only slightly off. */
  private static final float RECONCILE_SMOOTHING = 0.25f;
  /** Prediction error (px) above which we hard-snap instead of easing. */
  private static final float RECONCILE_SNAP = 64f;
  private static final int RELEASE_GRACE_MS = 45;

  /** On-screen pixels per world pixel — enlarges the whole window. */
  private static final float RENDER_SCALE = 1.75f;

  /** How fast the crosshair's fired-recently bloom settles back down, per second. */
  private static final float BLOOM_DECAY = 7f;

  private record Pending(long seq, float moveX, float moveY) {}

  private final TileMap map = TileMap.fromResource(GameConfig.MAP_RESOURCE, GameConfig.TILE);
  private final AssetManager assets = new AssetManager();
  private final Camera camera = new Camera(map, RENDER_SCALE);
  private final Renderer renderer = new Renderer(map, assets);
  private final Hud hud = new Hud(assets);
  private final WorldView view = new WorldView();
  private final GameClient client;

  // Local-player prediction.
  private Vec2 predicted;
  private final Deque<Pending> unacked = new ArrayDeque<>();
  private long lastSnapshotVersion = -1;

  // Input state (EDT only).
  private final Set<Integer> held = new HashSet<>();
  private final Map<Integer, Timer> pendingRelease = new HashMap<>();
  private float moveX;
  private float moveY;
  private boolean firing;
  private Weapon selectedWeapon = Weapon.PISTOL;
  private long lastFrameNanos;
  // Cursor in on-screen pixels (the crosshair is drawn unscaled, unlike the arena).
  private int screenMouseX;
  private int screenMouseY;
  private float recoilBloom;

  public Client(GameClient client) {
    this.client = client;
    setPreferredSize(new Dimension(camera.windowWidth(), camera.windowHeight()));
    setBackground(Theme.FLOOR);
    setFocusable(true);
    screenMouseX = camera.windowWidth() / 2;
    screenMouseY = camera.windowHeight() / 2;
    // The HUD draws its own crosshair; an arrow pointer on top of it would only be noise.
    setCursor(
        Toolkit.getDefaultToolkit()
            .createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank"));
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
            trackCursor(e);
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            trackCursor(e);
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

  /**
   * Remembers the cursor in raw window pixels. That is the only form worth storing: the camera
   * pans between mouse events, so a cached world position would go stale and drag the aim with it.
   */
  private void trackCursor(MouseEvent e) {
    screenMouseX = e.getX();
    screenMouseY = e.getY();
  }

  /** Angle (radians) from the local player toward the cursor, both in world coordinates. */
  private float aimAngle() {
    Vec2 p = predicted;
    if (p == null) {
      return 0f;
    }
    return (float) Math.atan2(camera.worldY(screenMouseY) - p.y, camera.worldX(screenMouseX) - p.x);
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
    if (firing && client.viewerReload() <= 0f && client.viewerAmmo() > 0) {
      recoilBloom = Math.min(1f, recoilBloom + 0.35f);
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

    view.update(dt, client.entities(), client.myPlayerId());
    camera.follow(cameraTarget(), dt);
    recoilBloom = Math.max(0f, recoilBloom - (float) (BLOOM_DECAY * dt));
  }

  /**
   * What the camera keeps centred: the predicted local kitten, falling back to its authoritative
   * position before prediction has started and to the middle of the map before either exists.
   */
  private Vec2 cameraTarget() {
    if (predicted != null) {
      return predicted;
    }
    int me = client.myPlayerId();
    EntityState mine = me < 0 ? null : client.entities().get(me);
    if (mine != null) {
      return Vec2.of(mine.x(), mine.y());
    }
    return Vec2.of(map.pixelWidth() * 0.5f, map.pixelHeight() * 0.5f);
  }

  private boolean localDead() {
    int me = client.myPlayerId();
    return me >= 0 && hpOf(me) <= 0f;
  }

  private float hpOf(int id) {
    EntityState e = client.entities().get(id);
    return e == null ? (float) GameConfig.PLAYER_MAX_HEALTH : e.hp();
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

    if (predicted == null || predicted.distance(target) > RECONCILE_SNAP) {
      predicted = target; // first snapshot or a desync worth snapping
    } else {
      predicted = predicted.add(target.sub(predicted).scale(RECONCILE_SMOOTHING));
    }
  }

  // ---- rendering ----------------------------------------------------------

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    Theme.quality(g2);

    // The arena is authored in world coordinates and magnified into the window through the camera;
    // the HUD is drawn afterwards at native resolution, so its text and panels stay sharp instead
    // of being scaled.
    AffineTransform screen = g2.getTransform();
    camera.apply(g2);
    renderer.draw(g2, scene());
    g2.setTransform(screen);

    hud.draw(g2, getWidth(), getHeight(), hudView());
  }

  private Renderer.Scene scene() {
    return new Renderer.Scene(
        client.entities(),
        client.myPlayerId(),
        view,
        predicted,
        aimAngle(),
        selectedWeapon,
        camera.visibleTiles());
  }

  private Hud.View hudView() {
    int me = client.myPlayerId();
    long enemies =
        client.entities().values().stream()
            .filter(e -> "rat".equals(e.kind()) || "mouse".equals(e.kind()))
            .count();
    return new Hud.View(
        me,
        me < 0 ? (float) GameConfig.PLAYER_MAX_HEALTH : hpOf(me),
        selectedWeapon,
        client.viewerAmmo(),
        client.viewerReload(),
        (int) enemies,
        me >= 0,
        localDead(),
        screenMouseX,
        screenMouseY,
        recoilBloom);
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
