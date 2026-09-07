package kittens.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
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
import kittens.common.net.EntityState;

/**
 * The client's rendering + input half. A 60 fps loop eases each entity's drawn position toward the
 * latest authoritative snapshot so 30 Hz updates look smooth; key state drives the input the
 * {@link GameClient} sends to the server.
 */
public final class Client extends JPanel {
  private static final Color FLOOR = new Color(30, 30, 36);
  private static final Color FLOOR_GRID = new Color(40, 40, 48);
  private static final Color WALL = new Color(70, 74, 92);
  private static final Color WALL_TOP = new Color(96, 100, 122);
  private static final Color SPAWN_TILE = new Color(38, 52, 44);

  private static final int FPS = 60;
  /** Per-frame easing toward the snapshot position (0..1); higher = snappier, less smooth. */
  private static final float SMOOTHING = 0.30f;
  private static final int RELEASE_GRACE_MS = 45;

  private final TileMap map = TileMap.fromResource(GameConfig.MAP_RESOURCE, GameConfig.TILE);
  private final AssetManager assets = new AssetManager();
  private final GameClient client;

  /** Interpolated on-screen position per entity id: {x, y}. */
  private final Map<Integer, float[]> renderPos = new HashMap<>();

  // Input state (touched only on the EDT).
  private final Set<Integer> held = new HashSet<>();
  private final Map<Integer, Timer> pendingRelease = new HashMap<>();
  private float lastMoveX;
  private float lastMoveY;

  public Client(GameClient client) {
    this.client = client;
    setPreferredSize(new Dimension((int) map.pixelWidth(), (int) map.pixelHeight()));
    setBackground(FLOOR);
    setFocusable(true);
    installInput();

    new Timer(1000 / FPS, e -> {
      interpolate();
      repaint();
    }).start();
  }

  private void installInput() {
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        Timer pending = pendingRelease.remove(code);
        if (pending != null) {
          pending.stop();
        }
        if (held.add(code)) {
          refreshMovement();
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        // X11 autorepeat emits release+press pairs while a key is held; defer the release
        // briefly and cancel it if the matching press arrives, so movement doesn't stutter.
        int code = e.getKeyCode();
        Timer grace = new Timer(RELEASE_GRACE_MS, ev -> {
          pendingRelease.remove(code);
          if (held.remove(code)) {
            refreshMovement();
          }
        });
        grace.setRepeats(false);
        pendingRelease.put(code, grace);
        grace.start();
      }
    });
  }

  private boolean down(int... codes) {
    for (int c : codes) {
      if (held.contains(c)) {
        return true;
      }
    }
    return false;
  }

  private void refreshMovement() {
    float moveX = (down(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1f : 0f)
        - (down(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1f : 0f);
    float moveY = (down(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1f : 0f)
        - (down(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1f : 0f);
    if (moveX != lastMoveX || moveY != lastMoveY) {
      lastMoveX = moveX;
      lastMoveY = moveY;
      client.sendInput(moveX, moveY, 0f, false);
    }
  }

  private void interpolate() {
    Map<Integer, EntityState> live = client.entities();
    renderPos.keySet().retainAll(live.keySet());
    for (EntityState e : live.values()) {
      float[] rp = renderPos.get(e.id());
      if (rp == null) {
        renderPos.put(e.id(), new float[] {e.x(), e.y()});
      } else {
        rp[0] += (e.x() - rp[0]) * SMOOTHING;
        rp[1] += (e.y() - rp[1]) * SMOOTHING;
      }
    }
  }

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
    int t = GameConfig.TILE;
    int me = client.myPlayerId();
    for (Map.Entry<Integer, float[]> entry : renderPos.entrySet()) {
      int id = entry.getKey();
      float cx = entry.getValue()[0];
      float cy = entry.getValue()[1];
      int drawX = Math.round(cx - t / 2f);
      int drawY = Math.round(cy - t / 2f);

      if (id == me) {
        g.setColor(new Color(120, 210, 255));
        g.drawOval(drawX - 2, drawY - 2, t + 3, t + 3);
      }
      g.drawImage(assets.kitten(GameConfig.kittenSprite(id)), drawX, drawY, t, t, null);
      g.setColor(Color.WHITE);
      g.drawString("P" + id, drawX, drawY - 4);
    }
  }

  private void drawHud(Graphics2D g) {
    g.setColor(new Color(255, 255, 255, 180));
    int me = client.myPlayerId();
    String who = me < 0 ? "connecting…" : "you are P" + me;
    g.drawString(who + "   —   WASD / arrow keys to move", 8, (int) map.pixelHeight() - 8);
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
