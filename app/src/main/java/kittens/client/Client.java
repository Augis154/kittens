package kittens.client;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.weapon.Weapon;

/**
 * The game window: wires {@link InputHandler}, {@link Predictor}, {@link WorldView}, {@link Camera},
 * {@link Renderer} and {@link Hud} together, sends input at {@link GameConfig#TICK_HZ} and renders
 * at {@link #FPS}. Everything here runs on the Swing EDT.
 */
public final class Client extends JPanel {
  private static final int FPS = 180;
  /** On-screen pixels per world pixel. */
  private static final float RENDER_SCALE = 1.75f;
  /** How fast the crosshair's fired-recently bloom settles, per second. */
  private static final float BLOOM_DECAY = 7f;

  private final TileMap map = TileMap.fromResource(GameConfig.MAP_RESOURCE, GameConfig.TILE);
  private final AssetManager assets = new AssetManager();
  private final Camera camera = new Camera(map, RENDER_SCALE);
  private final Renderer renderer = new Renderer(map, assets);
  private final Hud hud = new Hud(assets);
  private final WorldView view = new WorldView();
  private final Predictor predictor = new Predictor(map);
  private final InputHandler input;
  private final GameClient client;

  private long lastFrameNanos;
  private float recoilBloom;

  public Client(GameClient client) {
    this.client = client;
    setPreferredSize(new Dimension(camera.windowWidth(), camera.windowHeight()));
    setBackground(Theme.FLOOR);
    setFocusable(true);
    // The HUD draws its own crosshair, so hide the arrow pointer.
    setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank"));
    input = new InputHandler(this, camera.windowWidth() / 2, camera.windowHeight() / 2);

    new Timer(1000 / GameConfig.TICK_HZ, e -> sendInputTick()).start();
    new Timer(1000 / FPS, e -> {
      frame();
      repaint();
    }).start();
  }

  /** Fixed-rate: hand the current intent to the server and remember it for reconciliation. */
  private void sendInputTick() {
    if (client.myPlayerId() < 0) {
      return;
    }
    float aim = aimAngle();
    int weaponId = input.weaponId();
    boolean reload = input.consumeReload();
    if (localDead()) {
      client.sendInput(0f, 0f, aim, false, weaponId, false); // can still aim, cannot move
      return;
    }
    GameClient.Frame f = client.latest();
    if (input.firing() && f.reload() <= 0f && f.ammo() > 0) {
      recoilBloom = Math.min(1f, recoilBloom + 0.35f);
    }
    predictor.remember(client.sendInput(input.moveX(), input.moveY(), aim, input.firing(),
        weaponId, reload));
  }

  private void frame() {
    long now = System.nanoTime();
    double dt = lastFrameNanos == 0 ? 0 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
    lastFrameNanos = now;

    GameClient.Frame f = client.latest();
    int me = client.myPlayerId();
    predictor.update(dt, f, f.entity(me), input.moveX(), input.moveY());
    view.update(dt, f.entities(), me);
    camera.follow(cameraTarget(f), dt);
    recoilBloom = Math.max(0f, recoilBloom - (float) (BLOOM_DECAY * dt));
  }

  /** Angle from the local kitten to the cursor, both in world space. */
  private float aimAngle() {
    Vec2 p = predictor.position();
    if (p == null) {
      return 0f;
    }
    return (float) Math.atan2(camera.worldY(input.mouseY()) - p.y, camera.worldX(input.mouseX()) - p.x);
  }

  /** The predicted kitten, else its snapshot position, else the map centre. */
  private Vec2 cameraTarget(GameClient.Frame f) {
    if (predictor.position() != null) {
      return predictor.position();
    }
    EntityState mine = f.entity(client.myPlayerId());
    if (mine != null) {
      return Vec2.of(mine.x(), mine.y());
    }
    return Vec2.of(map.pixelWidth() * 0.5f, map.pixelHeight() * 0.5f);
  }

  private EntityState localEntity() {
    return client.latest().entity(client.myPlayerId());
  }

  private boolean localDead() {
    EntityState mine = localEntity();
    return mine != null && mine.hp() <= 0f;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    Theme.quality(g2);

    // Arena in world coordinates through the camera; HUD afterwards at native resolution so its
    // text stays crisp.
    AffineTransform screen = g2.getTransform();
    camera.apply(g2);
    renderer.draw(g2, scene());
    g2.setTransform(screen);
    hud.draw(g2, getWidth(), getHeight(), hudView());
  }

  private Renderer.Scene scene() {
    return new Renderer.Scene(client.latest().entities(), client.myPlayerId(), view,
        predictor.position(), aimAngle(), input.weapon(), camera.visibleTiles());
  }

  private Hud.View hudView() {
    GameClient.Frame f = client.latest();
    int me = client.myPlayerId();
    EntityState mine = f.entity(me);
    int enemies = (int) f.entities().values().stream().filter(e -> e.kind().isEnemy()).count();
    return new Hud.View(me, mine == null ? (float) GameConfig.PLAYER_MAX_HEALTH : mine.hp(),
        input.weapon(), f.ammo(), f.reload(), enemies, me >= 0, localDead(),
        input.mouseX(), input.mouseY(), recoilBloom);
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
