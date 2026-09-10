package kittens.client;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Map;
import kittens.common.GameConfig;
import kittens.common.map.Tile;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.weapon.Weapon;

/**
 * Draws the arena: tiles, entities, and cosmetic effects. Everything here is authored in world
 * coordinates — the caller has already scaled the graphics context — so no method may consult the
 * window size or the cursor. Screen-space overlay work belongs in {@link Hud}.
 *
 * <p>Stateless apart from the map and the asset cache: what to draw arrives in a {@link Scene}
 * every frame, which keeps this class a pure function of the latest snapshot plus {@link WorldView}
 * smoothing.
 */
final class Renderer {
  /**
   * Below this much immunity left, the protected kitten flickers faster. A post-hit i-frame is
   * shorter than this outright, so it reads as a single sharp flash, while the longer spawn grace
   * starts slow and quickens as it runs out.
   */
  private static final float GRACE_RUSH_SECONDS = 0.6f;

  /**
   * The inclusive block of tiles worth drawing this frame. The level is larger than the window, so
   * without this the map passes would redraw thousands of off-screen tiles every frame.
   * {@link Camera#visibleTiles()} produces it; the bounds are already clamped to the map.
   */
  record Tiles(int minCol, int minRow, int maxCol, int maxRow) {}

  /**
   * One frame's worth of world state.
   *
   * @param predicted local player's predicted position, or null before the first snapshot lands
   * @param aimAngle where the local player is pointing, from the cursor rather than the snapshot
   * @param tiles the on-screen tile range; the only thing here derived from where the camera is
   */
  record Scene(
      Map<Integer, EntityState> entities,
      int myPlayerId,
      WorldView view,
      Vec2 predicted,
      float aimAngle,
      Weapon localWeapon,
      Tiles tiles) {}

  private final TileMap map;
  private final AssetManager assets;

  Renderer(TileMap map, AssetManager assets) {
    this.map = map;
    this.assets = assets;
  }

  void draw(Graphics2D g, Scene scene) {
    drawMap(g, scene.tiles());
    drawEntities(g, scene);
    drawEffects(g, scene.view());
  }

  // ---- map ------------------------------------------------------------------

  private void drawMap(Graphics2D g, Tiles tiles) {
    int t = GameConfig.TILE;
    for (int row = tiles.minRow(); row <= tiles.maxRow(); row++) {
      for (int col = tiles.minCol(); col <= tiles.maxCol(); col++) {
        int px = col * t;
        int py = row * t;
        switch (map.tileAt(col, row)) {
          case WALL -> {}
          case SPAWN -> {
            g.setColor(Theme.SPAWN_TILE);
            g.fillRect(px, py, t, t);
            g.setColor(Theme.SPAWN_MARK);
            g.drawRect(px + 4, py + 4, t - 9, t - 9);
          }
          case FLOOR -> {
            // Checkerboard rather than a grid outline: the tiling reads without drawing lines
            // over every single tile edge.
            g.setColor((col + row) % 2 == 0 ? Theme.FLOOR : Theme.FLOOR_ALT);
            g.fillRect(px, py, t, t);
          }
        }
      }
    }

    // Walls in a second pass so their cast shadow lands on finished floor, never on a tile drawn
    // after them.
    for (int row = tiles.minRow(); row <= tiles.maxRow(); row++) {
      for (int col = tiles.minCol(); col <= tiles.maxCol(); col++) {
        if (map.tileAt(col, row) != Tile.WALL) {
          continue;
        }
        int px = col * t;
        int py = row * t;
        boolean openBelow = row + 1 >= map.height() || map.tileAt(col, row + 1) != Tile.WALL;
        boolean openAbove = row - 1 < 0 || map.tileAt(col, row - 1) != Tile.WALL;
        boolean openLeft = col - 1 < 0 || map.tileAt(col - 1, row) != Tile.WALL;
        boolean openRight = col + 1 >= map.width() || map.tileAt(col + 1, row) != Tile.WALL;

        if (openBelow) {
          g.setColor(Theme.WALL_SHADOW);
          g.fillRect(px, py + t, t, 5);
        }
        g.setColor(Theme.WALL);
        g.fillRect(px, py, t, t);
        // Shade only the exposed edges: a lit cap on top, dark seams elsewhere. Interior tiles of
        // a block stay flat, so the whole block reads as one mass.
        if (openAbove) {
          g.setColor(Theme.WALL_TOP);
          g.fillRect(px, py, t, 5);
        }
        g.setColor(Theme.WALL_EDGE);
        if (openBelow) {
          g.fillRect(px, py + t - 3, t, 3);
        }
        if (openLeft) {
          g.fillRect(px, py, 2, t);
        }
        if (openRight) {
          g.fillRect(px + t - 2, py, 2, t);
        }
      }
    }
  }

  // ---- entities -------------------------------------------------------------

  private void drawEntities(Graphics2D g, Scene scene) {
    Map<Integer, EntityState> entities = scene.entities();
    WorldView view = scene.view();
    int me = scene.myPlayerId();

    // 1. Bullets (dead-reckoned between snapshots)
    for (float[] b : view.bullets()) {
      drawBullet(g, b[0], b[1], b[2], (int) b[5]);
    }

    // 2. Enemies
    for (EntityState e : entities.values()) {
      if ("rat".equals(e.kind()) || "mouse".equals(e.kind())) {
        float[] pos = view.smoothed(e.id());
        drawEnemy(g, e, pos != null ? pos[0] : e.x(), pos != null ? pos[1] : e.y(),
            view.hitFlash(e.id()));
      }
    }

    // 3. Remote players
    for (EntityState e : entities.values()) {
      if (e.id() == me || !"cat".equals(e.kind())) {
        continue;
      }
      float[] pos = view.smoothed(e.id());
      drawKitten(
          g,
          e,
          pos != null ? pos[0] : e.x(),
          pos != null ? pos[1] : e.y(),
          e.angle(),
          Weapon.byId(e.weaponId()),
          false);
    }

    // 4. Local player: the prediction, falling back to raw authority until it exists
    EntityState mine = me < 0 ? null : entities.get(me);
    if (mine != null) {
      if (scene.predicted() != null) {
        drawKitten(g, mine, scene.predicted().x, scene.predicted().y, scene.aimAngle(),
            scene.localWeapon(), true);
      } else {
        drawKitten(g, mine, mine.x(), mine.y(), mine.angle(), Weapon.byId(mine.weaponId()), true);
      }
    }

    // 5. Explosions (on top)
    for (EntityState e : entities.values()) {
      if ("boom".equals(e.kind())) {
        drawBoom(g, e);
      }
    }
  }

  private void drawBullet(Graphics2D g, float bx, float by, float angle, int weaponId) {
    boolean rocket = weaponId == Weapon.BAZOOKA.id();
    float tail = rocket ? 18f : 11f;
    int hx = Math.round(bx);
    int hy = Math.round(by);
    int tailX = Math.round(bx - (float) Math.cos(angle) * tail);
    int tailY = Math.round(by - (float) Math.sin(angle) * tail);

    Stroke saved = g.getStroke();
    // Wide translucent pass first, bright core second: a glow without any blur work.
    g.setColor(rocket ? new Color(255, 130, 50, 70) : new Color(255, 210, 120, 60));
    g.setStroke(new BasicStroke(rocket ? 9f : 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.drawLine(tailX, tailY, hx, hy);
    g.setColor(rocket ? new Color(255, 176, 96) : new Color(255, 238, 176));
    g.setStroke(new BasicStroke(rocket ? 3.5f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.drawLine(tailX, tailY, hx, hy);
    g.setStroke(saved);

    int r = rocket ? 4 : 2;
    g.setColor(Color.WHITE);
    g.fillOval(hx - r, hy - r, r * 2, r * 2);
  }

  private void drawEnemy(Graphics2D g, EntityState e, float cx, float cy, float flash) {
    int t = GameConfig.TILE;
    boolean facingLeft = Math.cos(e.angle()) < 0;
    // Freshly hit enemies pop slightly larger and pick up a bright rim, which reads as impact
    // even when the health bar barely moves.
    int size = Math.round(t * (1f + 0.16f * flash));
    int x = Math.round(cx - size / 2f);
    int y = Math.round(cy - size / 2f);

    drawShadow(g, cx, cy, t);

    if (flash > 0f) {
      int r = Math.round(size * 0.55f);
      g.setColor(Theme.alpha(Color.WHITE, 0.30f * flash));
      g.fillOval(Math.round(cx) - r, Math.round(cy) - r, r * 2, r * 2);
    }

    BufferedImage img = assets.enemy(e.kind());
    if (img != null) {
      if (facingLeft) {
        g.drawImage(img, x + size, y, -size, size, null);
      } else {
        g.drawImage(img, x, y, size, size, null);
      }
    }

    // Health bar above the enemy, only once it has been hurt.
    double maxHp =
        "mouse".equals(e.kind()) ? GameConfig.MOUSE_MAX_HEALTH : GameConfig.RAT_MAX_HEALTH;
    float frac = Math.clamp(e.hp() / (float) maxHp, 0f, 1f);
    if (frac < 1f && frac > 0f) {
      int bw = Math.round(t * 0.7f);
      int bx = Math.round(cx - bw / 2f);
      int by = Math.round(cy - t / 2f) - 7;
      g.setColor(new Color(0, 0, 0, 170));
      g.fillRoundRect(bx - 1, by - 1, bw + 2, 5, 4, 4);
      g.setColor(Theme.healthColor(frac));
      g.fillRoundRect(bx, by, Math.max(1, Math.round(bw * frac)), 3, 3, 3);
    }
  }

  private void drawKitten(
      Graphics2D g, EntityState state, float cx, float cy, float angle, Weapon weapon,
      boolean self) {
    int id = state.id();
    int t = GameConfig.TILE;
    int x = Math.round(cx - t / 2f);
    int y = Math.round(cy - t / 2f);
    boolean facingLeft = Math.cos(angle) < 0;

    if (state.hp() <= 0f) {
      // Downed: a faint marker where the kitten will respawn from view soon.
      g.setColor(Theme.alpha(Theme.DANGER, 0.45f));
      Stroke savedStroke = g.getStroke();
      g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(x + 6, y + 6, x + t - 6, y + t - 6);
      g.drawLine(x + t - 6, y + 6, x + 6, y + t - 6);
      g.setStroke(savedStroke);
      nameplate(g, id, cx, y - 6, Theme.TEXT_DIM);
      return;
    }

    if (self) {
      // A short aim guide out of the kitten rather than a full line to the cursor: it shows the
      // firing direction without a bright streak across the whole arena.
      float guide = t * 1.6f;
      Stroke savedStroke = g.getStroke();
      g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(Theme.ACCENT_DIM);
      g.drawLine(
          Math.round(cx + (float) Math.cos(angle) * t * 0.55f),
          Math.round(cy + (float) Math.sin(angle) * t * 0.55f),
          Math.round(cx + (float) Math.cos(angle) * guide),
          Math.round(cy + (float) Math.sin(angle) * guide));
      // Selection ring, drawn as an ellipse on the floor so it doesn't halo the sprite.
      g.setColor(Theme.alpha(Theme.ACCENT, 0.55f));
      g.drawOval(
          Math.round(cx - t * 0.46f), Math.round(cy + t * 0.16f),
          Math.round(t * 0.92f), Math.round(t * 0.36f));
      g.setStroke(savedStroke);
    }

    drawShadow(g, cx, cy, t);

    // Immunity (spawn grace or a post-hit i-frame): pulse the kitten so its owner *and* their
    // teammates can see who is protected, quickening as it runs out rather than just stopping.
    float grace = state.invulnerableFor();
    Composite baseComposite = g.getComposite();
    if (grace > 0f) {
      double rate = grace < GRACE_RUSH_SECONDS ? 22.0 : 9.0;
      float pulse = (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 1e9 * rate));
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f + 0.5f * pulse));
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
    g.drawImage(assets.weapon(weapon.sprite()), 4, -w / 2, w, w, null);
    g.setTransform(saved);
    g.setComposite(baseComposite); // name tag and health bar stay solid

    // Health bar above the kitten — for other players only; the local player uses the heart HUD.
    float top = y - 8;
    if (!self) {
      float frac = Math.clamp(state.hp() / (float) GameConfig.PLAYER_MAX_HEALTH, 0f, 1f);
      if (frac < 1f) {
        int by = y - 8;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(x - 1, by - 1, t + 2, 5, 4, 4);
        g.setColor(Theme.healthColor(frac));
        g.fillRoundRect(x, by, Math.max(1, Math.round(t * frac)), 3, 3, 3);
        top = by - 4;
      }
    }

    nameplate(g, id, cx, top - 2, self ? Theme.ACCENT : Theme.TEXT);
  }

  private void drawBoom(Graphics2D g, EntityState e) {
    float r = e.angle();          // current expanding radius
    float maxR = e.hp();          // final radius
    float progress = maxR > 0f ? Math.clamp(r / maxR, 0f, 1f) : 1f;
    float fade = 1f - progress;
    int cx = Math.round(e.x());
    int cy = Math.round(e.y());
    int ir = Math.round(r);

    // Body, then a hot core that shrinks as the blast spreads, then the leading edge and a thin
    // shockwave running just ahead of it.
    g.setColor(Theme.alpha(new Color(255, 138, 52), 0.55f * fade));
    g.fillOval(cx - ir, cy - ir, ir * 2, ir * 2);
    int core = Math.round(ir * (0.55f - 0.35f * progress));
    if (core > 0) {
      g.setColor(Theme.alpha(new Color(255, 236, 196), 0.75f * fade));
      g.fillOval(cx - core, cy - core, core * 2, core * 2);
    }

    Stroke saved = g.getStroke();
    g.setStroke(new BasicStroke(3f));
    g.setColor(Theme.alpha(new Color(255, 224, 150), fade));
    g.drawOval(cx - ir, cy - ir, ir * 2, ir * 2);
    int wave = Math.round(ir * 1.18f);
    g.setStroke(new BasicStroke(1.5f));
    g.setColor(Theme.alpha(Color.WHITE, 0.35f * fade));
    g.drawOval(cx - wave, cy - wave, wave * 2, wave * 2);
    g.setStroke(saved);
  }

  /** Centred "P&lt;id&gt;" tag on a dark pill, so names stay legible over any tile or sprite. */
  private static void nameplate(Graphics2D g, int id, float cx, float baselineY, Color color) {
    g.setFont(Theme.FONT_WORLD);
    String tag = "P" + id;
    int w = Theme.textWidth(g, Theme.FONT_WORLD, tag);
    int x = Math.round(cx - w / 2f);
    int y = Math.round(baselineY);
    g.setColor(new Color(0, 0, 0, 130));
    g.fillRoundRect(x - 4, y - 9, w + 8, 12, 6, 6);
    g.setColor(color);
    g.drawString(tag, x, y);
  }

  /** Soft contact shadow beneath an actor, so sprites sit on the floor instead of floating. */
  private static void drawShadow(Graphics2D g, float cx, float cy, float size) {
    int w = Math.round(size * 0.8f);
    int h = Math.round(size * 0.34f);
    g.setColor(Theme.ACTOR_SHADOW);
    g.fillOval(Math.round(cx - w / 2f), Math.round(cy + size * 0.30f - h / 2f), w, h);
  }

  // ---- effects --------------------------------------------------------------

  /** Muzzle flashes, hit sparks, and floating damage — all client-side garnish. */
  private void drawEffects(Graphics2D g, WorldView view) {
    Composite baseComposite = g.getComposite();
    Stroke baseStroke = g.getStroke();
    for (WorldView.Fx fx : view.effects()) {
      float t = fx.progress();
      switch (fx.kind()) {
        case MUZZLE -> {
          float size = (fx.value() == 1 ? 16f : 10f) * (0.6f + 0.4f * t);
          g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t));
          AffineTransform saved = g.getTransform();
          g.translate(fx.x(), fx.y());
          g.rotate(fx.angle());
          g.setColor(Theme.alpha(Theme.WARN, 0.5f));
          g.fillOval(Math.round(-size), Math.round(-size * 0.6f),
              Math.round(size * 2.4f), Math.round(size * 1.2f));
          g.setColor(new Color(255, 246, 214));
          g.fillOval(Math.round(-size * 0.4f), Math.round(-size * 0.3f),
              Math.round(size), Math.round(size * 0.6f));
          g.setTransform(saved);
          g.setComposite(baseComposite);
        }
        case HIT -> {
          int r = Math.round(6 + (1f - t) * 12f);
          g.setStroke(new BasicStroke(2f));
          g.setColor(Theme.alpha(Color.WHITE, 0.8f * t));
          g.drawOval(Math.round(fx.x()) - r, Math.round(fx.y()) - r, r * 2, r * 2);
          g.setStroke(baseStroke);
        }
        case DAMAGE -> {
          g.setFont(Theme.FONT_WORLD);
          String txt = "-" + fx.value();
          int w = Theme.textWidth(g, Theme.FONT_WORLD, txt);
          // Fades only over the last third of its life, so the number is readable first.
          float fade = Math.clamp(t * 3f, 0f, 1f);
          g.setColor(Theme.alpha(Color.BLACK, 0.5f * fade));
          g.drawString(txt, Math.round(fx.x() - w / 2f) + 1, Math.round(fx.y()) + 1);
          g.setColor(Theme.alpha(Theme.WARN, fade));
          g.drawString(txt, Math.round(fx.x() - w / 2f), Math.round(fx.y()));
        }
      }
    }
    g.setComposite(baseComposite);
    g.setStroke(baseStroke);
  }
}
