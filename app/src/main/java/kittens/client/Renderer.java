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
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;
import kittens.common.weapon.Weapon;

/**
 * Draws the arena in world coordinates (the caller has already applied the camera), as a pure
 * function of a {@link Scene}. Screen-space overlay work belongs in {@link Hud}.
 */
final class Renderer {
  /** Below this much immunity left the protected kitten flickers faster. */
  private static final float GRACE_RUSH_SECONDS = 0.6f;
  /** Seconds of life left below which a pickup blinks, faster the closer it is to expiring. */
  private static final float PICKUP_BLINK_SECONDS = 3f;

  /** The inclusive block of tiles on screen; the map is bigger than the window, so only these are drawn. */
  record Tiles(int minCol, int minRow, int maxCol, int maxRow) {}

  /** One frame's worth of world state; {@code predicted} is null before the first snapshot. */
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
            g.setColor((col + row) % 2 == 0 ? Theme.FLOOR : Theme.FLOOR_ALT); // checkerboard
            g.fillRect(px, py, t, t);
          }
        }
      }
    }

    // Walls second, so their cast shadow lands on finished floor.
    for (int row = tiles.minRow(); row <= tiles.maxRow(); row++) {
      for (int col = tiles.minCol(); col <= tiles.maxCol(); col++) {
        if (map.tileAt(col, row) != Tile.WALL) {
          continue;
        }
        int px = col * t;
        int py = row * t;
        boolean openBelow = !wallAt(col, row + 1);
        boolean openAbove = !wallAt(col, row - 1);
        boolean openLeft = !wallAt(col - 1, row);
        boolean openRight = !wallAt(col + 1, row);

        if (openBelow) {
          g.setColor(Theme.WALL_SHADOW);
          g.fillRect(px, py + t, t, 5);
        }
        g.setColor(Theme.WALL);
        g.fillRect(px, py, t, t);
        // Only exposed edges are shaded, so a block of wall reads as one mass.
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

  /** Unlike {@code TileMap.isWall}, outside the map counts as open so boundary walls keep their edge shading. */
  private boolean wallAt(int col, int row) {
    return map.inBounds(col, row) && map.tileAt(col, row) == Tile.WALL;
  }

  // ---- entities -------------------------------------------------------------

  /** Pickups, bullets, enemies, remote kittens, the local kitten, explosions — in that order. */
  private void drawEntities(Graphics2D g, Scene scene) {
    Map<Integer, EntityState> entities = scene.entities();
    WorldView view = scene.view();
    int me = scene.myPlayerId();

    for (EntityState e : entities.values()) {
      if (e.kind().isPickup()) {
        drawPickup(g, e, e.kind() == EntityKind.HEALTH);
      }
    }
    for (WorldView.Bullet b : view.bullets()) {
      drawBullet(g, b.pos(), b.angle(), b.weapon() == Weapon.LAUNCHER);
    }
    for (EntityState e : entities.values()) {
      if (e.kind().isEnemy()) {
        drawEnemy(g, e, view.positionOf(e), view.hitFlash(e.id()));
      }
    }
    for (EntityState e : entities.values()) {
      if (e.kind() == EntityKind.CAT && e.id() != me) {
        drawKitten(g, e, view.positionOf(e), e.angle(), Weapon.byId(e.weaponId()), false);
      }
    }
    EntityState mine = entities.get(me);
    if (mine != null) {
      if (scene.predicted() != null) {
        drawKitten(g, mine, scene.predicted(), scene.aimAngle(), scene.localWeapon(), true);
      } else {
        drawKitten(g, mine, Vec2.of(mine.x(), mine.y()), mine.angle(),
            Weapon.byId(mine.weaponId()), true);
      }
    }
    for (EntityState e : entities.values()) {
      if (e.kind() == EntityKind.BOOM) {
        drawBoom(g, e);
      }
    }
  }

  /** A hovering crate: green with a cross for a medkit, amber with a clip of rounds for ammo. */
  private void drawPickup(Graphics2D g, EntityState e, boolean health) {
    int size = GameConfig.PICKUP_SIZE;
    Color tint = health ? Theme.GOOD : Theme.WARN;
    double clock = System.nanoTime() / 1e9;
    float bob = (float) Math.sin(clock * 2.6 + e.id()) * 2.5f; // offset by id so crates don't pulse in unison

    drawShadow(g, e.x(), e.y(), size);
    Composite baseComposite = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, blinkAlpha(e.hp(), clock)));

    int glowW = Math.round(size * 1.75f);
    int glowH = Math.round(size * 0.9f);
    g.setColor(Theme.alpha(tint, 0.16f));
    g.fillOval(Math.round(e.x() - glowW / 2f), Math.round(e.y() - glowH / 2f), glowW, glowH);

    int x = Math.round(e.x() - size / 2f);
    int y = Math.round(e.y() - size / 2f + bob);
    g.setColor(new Color(18, 20, 26, 230));
    g.fillRoundRect(x, y, size, size, 6, 6);
    g.setColor(tint);
    g.drawRoundRect(x, y, size - 1, size - 1, 6, 6);

    // Icons are drawn, not sprited: a bitmap scaled to 20px turns to mush.
    if (health) {
      int arm = 4;
      int span = size - 8;
      g.fillRoundRect(Math.round(x + (size - arm) / 2f), y + 4, arm, span, 2, 2);
      g.fillRoundRect(x + 4, Math.round(y + (size - arm) / 2f), span, arm, 2, 2);
    } else {
      int roundW = 3;
      int gap = 3;
      int startX = Math.round(x + size / 2f - (3 * roundW + 2 * gap) / 2f);
      for (int i = 0; i < 3; i++) {
        g.fillRoundRect(startX + i * (roundW + gap), y + 5, roundW, size - 10, 3, 3);
      }
    }
    g.setComposite(baseComposite);
  }

  /** Solid until nearly expired, then a quickening blink that never reaches 0. */
  private static float blinkAlpha(float life, double clock) {
    if (life >= PICKUP_BLINK_SECONDS) {
      return 1f;
    }
    double rate = 7.0 + (PICKUP_BLINK_SECONDS - life) * 4.0;
    return 0.3f + 0.7f * (float) Math.abs(Math.sin(clock * rate));
  }

  private void drawBullet(Graphics2D g, Vec2 at, float angle, boolean rocket) {
    float tail = rocket ? 18f : 11f;
    int hx = Math.round(at.x);
    int hy = Math.round(at.y);
    int tailX = Math.round(at.x - (float) Math.cos(angle) * tail);
    int tailY = Math.round(at.y - (float) Math.sin(angle) * tail);

    Stroke saved = g.getStroke();
    // Wide translucent pass then a bright core: a glow without blur work.
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

  private void drawEnemy(Graphics2D g, EntityState e, Vec2 at, float flash) {
    int t = GameConfig.TILE;
    int size = Math.round(t * (1f + 0.16f * flash)); // a fresh hit pops the sprite slightly larger
    int x = Math.round(at.x - size / 2f);
    int y = Math.round(at.y - size / 2f);

    drawShadow(g, at.x, at.y, t);
    if (flash > 0f) {
      int r = Math.round(size * 0.55f);
      g.setColor(Theme.alpha(Color.WHITE, 0.30f * flash));
      g.fillOval(Math.round(at.x) - r, Math.round(at.y) - r, r * 2, r * 2);
    }
    drawFlipped(g, assets.enemy(e.kind()), x, y, size, Math.cos(e.angle()) < 0);

    double maxHp = e.kind() == EntityKind.MOUSE ? GameConfig.MOUSE_MAX_HEALTH : GameConfig.RAT_MAX_HEALTH;
    float frac = Math.clamp(e.hp() / (float) maxHp, 0f, 1f);
    if (frac < 1f && frac > 0f) {
      healthBar(g, Math.round(at.x - t * 0.35f), Math.round(at.y - t / 2f) - 7, Math.round(t * 0.7f), frac);
    }
  }

  private void drawKitten(Graphics2D g, EntityState state, Vec2 at, float angle, Weapon weapon,
      boolean self) {
    int id = state.id();
    int t = GameConfig.TILE;
    int x = Math.round(at.x - t / 2f);
    int y = Math.round(at.y - t / 2f);
    boolean facingLeft = Math.cos(angle) < 0;
    Stroke baseStroke = g.getStroke();

    if (state.hp() <= 0f) {
      // Downed: a faint cross where the kitten was.
      g.setColor(Theme.alpha(Theme.DANGER, 0.45f));
      g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(x + 6, y + 6, x + t - 6, y + t - 6);
      g.drawLine(x + t - 6, y + 6, x + 6, y + t - 6);
      g.setStroke(baseStroke);
      nameplate(g, id, at.x, y - 6, Theme.TEXT_DIM);
      return;
    }

    if (self) {
      // Short aim guide and a floor ring, instead of a bright line to the cursor.
      float guide = t * 1.6f;
      g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(Theme.ACCENT_DIM);
      g.drawLine(Math.round(at.x + (float) Math.cos(angle) * t * 0.55f),
          Math.round(at.y + (float) Math.sin(angle) * t * 0.55f),
          Math.round(at.x + (float) Math.cos(angle) * guide),
          Math.round(at.y + (float) Math.sin(angle) * guide));
      g.setColor(Theme.alpha(Theme.ACCENT, 0.55f));
      g.drawOval(Math.round(at.x - t * 0.46f), Math.round(at.y + t * 0.16f),
          Math.round(t * 0.92f), Math.round(t * 0.36f));
      g.setStroke(baseStroke);
    }

    drawShadow(g, at.x, at.y, t);

    // Immunity pulses the sprite so teammates can see who is protected, quickening as it runs out.
    float grace = state.invulnerableFor();
    Composite baseComposite = g.getComposite();
    if (grace > 0f) {
      double rate = grace < GRACE_RUSH_SECONDS ? 22.0 : 9.0;
      float pulse = (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 1e9 * rate));
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f + 0.5f * pulse));
    }
    drawFlipped(g, assets.kitten(GameConfig.kittenSprite(id)), x, y, t, facingLeft);

    // Weapon rotated around the kitten toward the aim.
    int w = 22;
    AffineTransform saved = g.getTransform();
    g.translate(at.x, at.y);
    g.rotate(angle);
    if (facingLeft) {
      g.scale(1, -1); // keep the gun upright when aiming left
    }
    g.drawImage(assets.weapon(weapon.sprite()), 4, -w / 2, w, w, null);
    g.setTransform(saved);
    g.setComposite(baseComposite);

    // Remote kittens get a health bar; the local one uses the heart HUD.
    float top = y - 8;
    if (!self) {
      float frac = Math.clamp(state.hp() / (float) GameConfig.PLAYER_MAX_HEALTH, 0f, 1f);
      if (frac < 1f) {
        healthBar(g, x, y - 8, t, frac);
        top = y - 12;
      }
    }
    nameplate(g, id, at.x, top - 2, self ? Theme.ACCENT : Theme.TEXT);
  }

  private void drawBoom(Graphics2D g, EntityState e) {
    float r = e.angle(); // current radius (see EntityState)
    float maxR = e.hp(); // final radius
    float progress = maxR > 0f ? Math.clamp(r / maxR, 0f, 1f) : 1f;
    float fade = 1f - progress;
    int cx = Math.round(e.x());
    int cy = Math.round(e.y());
    int ir = Math.round(r);

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

  /** Sprite drawn at {@code size}, mirrored horizontally when facing left. */
  private static void drawFlipped(Graphics2D g, BufferedImage img, int x, int y, int size,
      boolean flip) {
    if (flip) {
      g.drawImage(img, x + size, y, -size, size, null);
    } else {
      g.drawImage(img, x, y, size, size, null);
    }
  }

  private static void healthBar(Graphics2D g, int x, int y, int width, float frac) {
    g.setColor(new Color(0, 0, 0, 170));
    g.fillRoundRect(x - 1, y - 1, width + 2, 5, 4, 4);
    g.setColor(Theme.healthColor(frac));
    g.fillRoundRect(x, y, Math.max(1, Math.round(width * frac)), 3, 3, 3);
  }

  /** Centred "P&lt;id&gt;" tag on a dark pill. */
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

  /** Soft contact shadow so sprites sit on the floor instead of floating. */
  private static void drawShadow(Graphics2D g, float cx, float cy, float size) {
    int w = Math.round(size * 0.8f);
    int h = Math.round(size * 0.34f);
    g.setColor(Theme.ACTOR_SHADOW);
    g.fillOval(Math.round(cx - w / 2f), Math.round(cy + size * 0.30f - h / 2f), w, h);
  }

  // ---- effects --------------------------------------------------------------

  private void drawEffects(Graphics2D g, WorldView view) {
    Composite baseComposite = g.getComposite();
    Stroke baseStroke = g.getStroke();
    for (WorldView.Fx fx : view.effects()) {
      float t = fx.progress();
      int cx = Math.round(fx.pos.x);
      int cy = Math.round(fx.pos.y);
      switch (fx.kind) {
        case MUZZLE -> {
          float size = (fx.accent ? 16f : 10f) * (0.6f + 0.4f * t);
          g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t));
          AffineTransform saved = g.getTransform();
          g.translate(fx.pos.x, fx.pos.y);
          g.rotate(fx.angle);
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
          g.drawOval(cx - r, cy - r, r * 2, r * 2);
          g.setStroke(baseStroke);
        }
        case PICKUP -> {
          int r = Math.round(6 + (1f - t) * 18f);
          g.setStroke(new BasicStroke(2.5f));
          g.setColor(Theme.alpha(fx.accent ? Theme.GOOD : Theme.WARN, 0.9f * t));
          g.drawOval(cx - r, cy - r, r * 2, r * 2);
          g.setStroke(baseStroke);
        }
        case DAMAGE -> {
          g.setFont(Theme.FONT_WORLD);
          String txt = "-" + fx.amount;
          int w = Theme.textWidth(g, Theme.FONT_WORLD, txt);
          float fade = Math.clamp(t * 3f, 0f, 1f); // readable first, fades over the last third
          g.setColor(Theme.alpha(Color.BLACK, 0.5f * fade));
          g.drawString(txt, Math.round(fx.pos.x - w / 2f) + 1, cy + 1);
          g.setColor(Theme.alpha(Theme.WARN, fade));
          g.drawString(txt, Math.round(fx.pos.x - w / 2f), cy);
        }
      }
    }
    g.setComposite(baseComposite);
    g.setStroke(baseStroke);
  }
}
