package kittens.client;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import kittens.client.audio.SoundSystem;
import kittens.client.events.GameEvent;
import kittens.client.events.GameEvents;
import kittens.client.settings.DisplayPrefs;
import kittens.client.settings.Settings;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.TcpTransport;
import kittens.common.weapon.Weapon;
import kittens.common.weapon.WeaponFactory;
import kittens.server.Server;

/**
 * The game window: wires {@link InputHandler}, {@link Predictor}, {@link WorldView}, {@link Camera},
 * {@link Renderer} and {@link Hud} together, sends input at {@link GameConfig#TICK_HZ} and renders
 * at the player's {@link DisplayPrefs#fps()}. Everything here runs on the Swing EDT.
 */
public final class Client extends JPanel {
  /**
   * Captured once: the window is sized at launch and never resized, so a scale changed in the
   * settings menu waits for the next launch rather than mismatching the window it would draw into.
   */
  private static final float RENDER_SCALE = Settings.get().display().renderScale();
  /** How fast the crosshair's fired-recently bloom settles, per second. */
  private static final float BLOOM_DECAY = 7f;

  /** The level the server sent, not a resource both ends load independently. */
  private final TileMap map;
  private final AssetManager assets = new AssetManager();
  private final Camera camera;
  private final Renderer renderer;
  private final Hud hud = new Hud(assets);
  private final GameEvents events = new GameEvents();
  private final Effects effects = new Effects();
  private final WorldView view = new WorldView(events);
  private final Predictor predictor;
  private final InputHandler input;
  private final GameClient client;
  private final WeaponFactory family;
  private final Timer frameTimer;

  private long lastFrameNanos;
  private float recoilBloom;
  private float heardHp = Float.NaN;
  private float heardReload;

  public Client(GameClient client, TileMap map, WeaponFactory family) {
    this.client = client;
    this.map = map;
    this.family = family;
    // Field initializers run before the constructor body, so anything that needs the map is built
    // here, once the level is known.
    this.camera = new Camera(map, RENDER_SCALE);
    this.renderer = new Renderer(map, assets);
    this.predictor = new Predictor(map);
    events.subscribe(effects);
    events.subscribe(new SoundObserver(SoundSystem.get()));
    setPreferredSize(new Dimension(camera.windowWidth(), camera.windowHeight()));
    setBackground(Theme.FLOOR);
    setFocusable(true);
    // The HUD draws its own crosshair, so hide the arrow pointer.
    setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank"));
    input = new InputHandler(this, camera.windowWidth() / 2, camera.windowHeight() / 2, family);

    new Timer(1000 / GameConfig.TICK_HZ, e -> sendInputTick()).start();
    frameTimer = new Timer(1000 / Settings.get().display().fps(), e -> {
      frame();
      repaint();
    });
    frameTimer.start();
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          openSettings();
        }
      }
    });
  }

  /**
   * The match keeps running behind the menu, so the kitten is left standing still rather than
   * walking on with whatever was held when Esc was pressed. On the way back the name and colour go
   * to the server unconditionally: one small message, and nothing to track about what changed.
   */
  private void openSettings() {
    input.release();
    showSettings((JFrame) SwingUtilities.getWindowAncestor(this), this, assets, true, () -> {
      frameTimer.setDelay(1000 / Settings.get().display().fps());
      client.sendProfile();
    });
  }

  /** Swaps {@code from} for the settings screen; leaving it swaps {@code from} back in. */
  static void showSettings(JFrame frame, JComponent from, AssetManager assets, boolean inGame,
      Runnable onReturn) {
    SettingsMenu menu = new SettingsMenu(windowSize(), assets, inGame, () -> {
      frame.setContentPane(from);
      frame.revalidate();
      frame.repaint();
      onReturn.run();
      from.requestFocusInWindow();
    });
    frame.setContentPane(menu);
    frame.revalidate();
    frame.repaint();
    menu.requestFocusInWindow();
  }

  /** Fixed-rate: hand the current intent to the server and remember it for reconciliation. */
  private void sendInputTick() {
    if (client.myPlayerId() < 0) {
      return;
    }
    float aim = aimAngle();
    int slot = input.slot();
    boolean reload = input.consumeReload();
    if (localDead()) {
      client.sendInput(0f, 0f, aim, false, slot, false); // can still aim, cannot move
      return;
    }
    GameClient.Frame f = client.latest();
    if (input.firing() && f.reload() <= 0f && f.ammo() > 0) {
      recoilBloom = Math.min(1f, recoilBloom + 0.35f);
    }
    predictor.remember(client.sendInput(input.moveX(), input.moveY(), aim, input.firing(),
        slot, reload));
  }

  private void frame() {
    long now = System.nanoTime();
    double dt = lastFrameNanos == 0 ? 0 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
    lastFrameNanos = now;

    GameClient.Frame f = client.latest();
    int me = client.myPlayerId();
    EntityState mine = f.entity(me);
    predictor.update(dt, f, mine, input.moveX(), input.moveY());
    view.update(dt, f.entities(), me);
    effects.advance(dt);
    Vec2 target = cameraTarget(f);
    camera.follow(target, dt);
    SoundSystem.get().listenAt(target);
    publishLocalEvents(f, mine);
    recoilBloom = Math.max(0f, recoilBloom - (float) (BLOOM_DECAY * dt));
  }

  /** The local kitten's own events, which come off the snapshot rather than from anything seen. */
  private void publishLocalEvents(GameClient.Frame f, EntityState mine) {
    float hp = mine == null ? Float.NaN : mine.hp();
    if (hp < heardHp) {
      events.publish(new GameEvent.PlayerHurt());
    }
    heardHp = hp;
    if (f.reload() > 0f && heardReload <= 0f) {
      events.publish(new GameEvent.ReloadStarted());
    }
    heardReload = f.reload();
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
    GameClient.Frame f = client.latest();
    return new Renderer.Scene(f.entities(), client.myPlayerId(), view, effects,
        predictor.position(), aimAngle(), input.weapon(), camera.visibleTiles(), f.players());
  }

  private Hud.View hudView() {
    GameClient.Frame f = client.latest();
    int me = client.myPlayerId();
    EntityState mine = f.entity(me);
    int enemies = (int) f.entities().values().stream().filter(e -> e.kind().isEnemy()).count();
    return new Hud.View(me, f.players().get(me),
        mine == null ? (float) GameConfig.PLAYER_MAX_HEALTH : mine.hp(),
        input.weapon(), family, f.ammo(), f.reload(), enemies, f.wave(), f.intermission(),
        me >= 0, localDead(), input.mouseX(), input.mouseY(), recoilBloom);
  }

  /** The window's size, so the arsenal menu and the game fill the same frame. */
  static Dimension windowSize() {
    return Camera.windowSize(RENDER_SCALE);
  }

  /**
   * Connects with the chosen arsenal, waits for the server's level, then swaps the menu for the
   * game. The wait blocks, so it runs off the EDT; every outcome is handed back onto it. Offline
   * starts the server in this process and joins it over the loopback; from there on the game cannot
   * tell the difference.
   */
  private static void join(JFrame frame, String host, WeaponFactory family, boolean offline,
      Consumer<String> fail) {
    Thread joiner = new Thread(() -> {
      GameClient net = null;
      try {
        net = new GameClient(offline
            ? Server.getInstance().loopback().connect()
            : TcpTransport.connect(host, GameConfig.PORT));
        net.start(family);
        TileMap level = net.awaitMap(5000);
        GameClient joined = net;
        SwingUtilities.invokeLater(() -> {
          Client game = new Client(joined, level, family);
          frame.setContentPane(game);
          frame.revalidate();
          game.requestFocusInWindow();
        });
      } catch (IOException e) {
        String where = offline ? "the offline server" : host + ":" + GameConfig.PORT;
        SwingUtilities.invokeLater(() -> fail.accept("Cannot reach " + where));
      } catch (InterruptedException | IllegalStateException e) {
        if (net != null) {
          net.close(); // otherwise the server keeps an idle kitten for a client that gave up
        }
        SwingUtilities.invokeLater(() -> fail.accept("The server did not send a level"));
      }
    }, "client-join");
    joiner.setDaemon(true);
    joiner.start();
  }

  public static void main(String[] args) {
    Settings.get(); // reads the settings file here, not in the first EDT frame
    SoundSystem.get(); // likewise opens the device and synthesizes
    String host = args.length > 0 ? args[0] : "localhost";
    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame("Kittens");
      AssetManager assets = new AssetManager();
      ArsenalMenu menu = new ArsenalMenu(assets, windowSize(), host,
          (family, offline, fail) -> join(frame, host, family, offline, fail));
      menu.onSettings(() -> showSettings(frame, menu, assets, false, () -> { }));
      frame.add(menu);
      frame.pack();
      frame.setResizable(false);
      frame.setLocationRelativeTo(null);
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setVisible(true);
      menu.requestFocusInWindow();
    });
  }
}
