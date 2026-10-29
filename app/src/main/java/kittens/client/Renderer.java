package kittens.client;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Random;
import kittens.common.GameConfig;
import kittens.common.map.Tile;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.Buff;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;
import kittens.common.net.PlayerInfo;
import kittens.common.weapon.Weapon;
import kittens.common.weapon.WeaponFactory;

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
      Effects effects,
      Vec2 predicted,
      float aimAngle,
      Weapon localWeapon,
      Tiles tiles,
      Map<Integer, PlayerInfo> players) {}

  private final TileMap map;
  private final AssetManager assets;

  Renderer(TileMap map, AssetManager assets) {
    this.map = map;
    this.assets = assets;
  }

  void draw(Graphics2D g, Scene scene) {
    drawMap(g, scene.tiles());
    drawEntities(g, scene);
    drawEffects(g, scene.effects());
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

  /** Crates and pickups, bullets, enemies, remote kittens, the local kitten, explosions — in that order. */
  private void drawEntities(Graphics2D g, Scene scene) {
    Map<Integer, EntityState> entities = scene.entities();
    WorldView view = scene.view();
    int me = scene.myPlayerId();

    for (EntityState e : entities.values()) {
      if (e.kind() == EntityKind.CRATE) {
        drawCrate(g, e, scene.effects().hitFlash(e.id()));
      } else if (e.kind().isPickup()) {
        drawPickup(g, e);
      }
    }
    for (WorldView.Bullet b : view.bullets()) {
      drawBullet(g, b.pos(), b.angle(), b.weapon().explosive());
    }
    for (EntityState e : entities.values()) {
      if (e.kind().isEnemy()) {
        drawEnemy(g, e, view.positionOf(e), scene.effects().hitFlash(e.id()));
      }
    }
    for (EntityState e : entities.values()) {
      if (e.kind() == EntityKind.CAT && e.id() != me) {
        drawKitten(g, e, view.positionOf(e), e.angle(), WeaponFactory.decode(e.weaponId()), false,
            scene.players().get(e.id()));
      }
    }
    EntityState mine = entities.get(me);
    if (mine != null) {
      if (scene.predicted() != null) {
        drawKitten(g, mine, scene.predicted(), scene.aimAngle(), scene.localWeapon(), true,
            scene.players().get(me));
      } else {
        drawKitten(g, mine, Vec2.of(mine.x(), mine.y()), mine.angle(),
            WeaponFactory.decode(mine.weaponId()), true, scene.players().get(me));
      }
    }
    for (EntityState e : entities.values()) {
      if (e.kind() == EntityKind.BOOM) {
        drawBoom(g, e);
      }
    }
  }

  /**
   * A wooden supply crate. Nothing on it says what is inside — that is the point — but it cracks as
   * it is shot and jolts on each hit, so a kitten can tell how close it is to breaking.
   */
  private void drawCrate(Graphics2D g, EntityState e, float flash) {
    int size = GameConfig.CRATE_SIZE;
    float damage = 1f - Math.clamp(e.hp() / (float) GameConfig.CRATE_HEALTH, 0f, 1f);
    // A hit jolts it sideways for a moment.
    float jolt = flash > 0f ? (float) Math.sin(flash * 40f) * 2.5f * flash : 0f;
    float cx = e.x() + jolt;
    drawShadow(g, cx, e.y(), size);
    int x = Math.round(cx - size / 2f);
    int y = Math.round(e.y() - size / 2f);

    g.setColor(Theme.CRATE_WOOD);
    g.fillRect(x, y, size, size);
    g.setColor(Theme.CRATE_PLANK); // plank seams
    for (int i = 1; i < 3; i++) {
      g.fillRect(x, y + i * size / 3 - 1, size, 2);
    }
    Stroke baseStroke = g.getStroke();
    g.setStroke(new BasicStroke(3f));
    g.setColor(Theme.CRATE_PLANK); // the diagonal brace
    g.drawLine(x + 3, y + size - 3, x + size - 3, y + 3);
    g.setStroke(new BasicStroke(2f));
    g.setColor(Theme.CRATE_EDGE);
    g.drawRect(x + 1, y + 1, size - 2, size - 2);
    g.setColor(new Color(160, 160, 170)); // nail heads in the corners
    for (int[] c : new int[][] {{4, 4}, {size - 6, 4}, {4, size - 6}, {size - 6, size - 6}}) {
      g.fillRect(x + c[0], y + c[1], 2, 2);
    }

    // Cracks: more of them, longer, as it takes damage. Seeded by id so they don't crawl each frame.
    if (damage > 0.05f) {
      Random r = new Random(e.id());
      g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(new Color(36, 22, 10, 220));
      int cracks = 1 + Math.round(damage * 4);
      for (int i = 0; i < cracks; i++) {
        float px = x + 4 + r.nextFloat() * (size - 8);
        float py = y + 4 + r.nextFloat() * (size - 8);
        double a = r.nextDouble() * Math.PI * 2;
        for (int seg = 0; seg < 3; seg++) {
          float len = (3f + r.nextFloat() * 4f) * (0.5f + damage);
          a += r.nextDouble() - 0.5;
          float nx = Math.clamp(px + (float) Math.cos(a) * len, x + 2, x + size - 2);
          float ny = Math.clamp(py + (float) Math.sin(a) * len, y + 2, y + size - 2);
          g.drawLine(Math.round(px), Math.round(py), Math.round(nx), Math.round(ny));
          px = nx;
          py = ny;
        }
      }
    }
    if (flash > 0f) {
      g.setColor(Theme.alpha(Color.WHITE, 0.35f * flash));
      g.fillRect(x, y, size, size);
    }
    g.setStroke(baseStroke);
  }

  /** A hovering item, tinted and marked by what it does; it blinks as it nears expiry. */
  private void drawPickup(Graphics2D g, EntityState e) {
    int size = GameConfig.PICKUP_SIZE;
    Color tint = Theme.pickupTint(e.kind());
    double clock = System.nanoTime() / 1e9;
    float bob = (float) Math.sin(clock * 2.6 + e.id()) * 2.5f; // offset by id so items don't pulse in unison

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
    pickupIcon(g, e.kind(), x, y, size);
    g.setComposite(baseComposite);
  }

  /** The mark inside a pickup's box; the colour is already set to its tint. */
  private static void pickupIcon(Graphics2D g, EntityKind kind, int x, int y, int size) {
    Stroke baseStroke = g.getStroke();
    float cx = x + size / 2f;
    float cy = y + size / 2f;
    switch (kind) {
      case HEALTH -> {
        int arm = 4;
        int span = size - 8;
        g.fillRoundRect(Math.round(x + (size - arm) / 2f), y + 4, arm, span, 2, 2);
        g.fillRoundRect(x + 4, Math.round(y + (size - arm) / 2f), span, arm, 2, 2);
      }
      case AMMO -> {
        int roundW = 3;
        int gap = 3;
        int startX = Math.round(x + size / 2f - (3 * roundW + 2 * gap) / 2f);
        for (int i = 0; i < 3; i++) {
          g.fillRoundRect(startX + i * (roundW + gap), y + 5, roundW, size - 10, 3, 3);
        }
      }
      case DOUBLE_DAMAGE -> {
        g.setFont(Theme.FONT_SMALL);
        String txt = "x2";
        int w = Theme.textWidth(g, Theme.FONT_SMALL, txt);
        g.drawString(txt, Math.round(cx - w / 2f), Math.round(cy + 4.5f));
      }
      case SHIELD -> {
        Path2D.Float shield = new Path2D.Float();
        shield.moveTo(cx, y + 4);
        shield.lineTo(x + size - 5, y + 6);
        shield.quadTo(x + size - 5, y + size - 6, cx, y + size - 3);
        shield.quadTo(x + 5, y + size - 6, x + 5, y + 6);
        shield.closePath();
        g.fill(shield);
      }
      case CATNIP -> { // a leaf on its stem
        AffineTransform saved = g.getTransform();
        g.translate(cx, cy);
        g.rotate(-Math.PI / 4);
        g.fillOval(-3, -7, 7, 14);
        g.setTransform(saved);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(Math.round(cx - 1), Math.round(cy + 1), x + 4, y + size - 4);
      }
      case NINE_LIVES -> { // a paw print
        g.fillOval(Math.round(cx - 4.5f), Math.round(cy - 1), 9, 7);
        g.fillOval(Math.round(cx - 7), Math.round(cy - 6), 4, 4);
        g.fillOval(Math.round(cx - 2), Math.round(cy - 8), 4, 4);
        g.fillOval(Math.round(cx + 3), Math.round(cy - 6), 4, 4);
      }
      default -> { }
    }
    g.setStroke(baseStroke);
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

  /** {@code who} is null only for the moment before the first roster arrives. */
  private void drawKitten(Graphics2D g, EntityState state, Vec2 at, float angle, Weapon weapon,
      boolean self, PlayerInfo who) {
    int id = state.id();
    String name = who != null ? who.name() : "P" + id;
    String color = who != null ? who.color() : GameConfig.kittenSprite(id);
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
      nameplate(g, name, at.x, y - 6, Theme.TEXT_DIM);
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
    int buffs = state.buffs();
    double clock = System.nanoTime() / 1e9;
    if (Buff.DOUBLE_DAMAGE.in(buffs)) { // a hot glow at the kitten's feet
      float pulse = (float) (0.75 + 0.25 * Math.sin(clock * 8 + id));
      g.setColor(Theme.alpha(Theme.POWER_DAMAGE, 0.32f * pulse));
      g.fillOval(Math.round(at.x - t * 0.62f), Math.round(at.y - t * 0.1f),
          Math.round(t * 1.24f), Math.round(t * 0.62f));
    }

    // Immunity pulses the sprite so teammates can see who is protected, quickening as it runs out.
    float grace = state.invulnerableFor();
    Composite baseComposite = g.getComposite();
    if (grace > 0f) {
      double rate = grace < GRACE_RUSH_SECONDS ? 22.0 : 9.0;
      float pulse = (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 1e9 * rate));
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f + 0.5f * pulse));
    }
    drawFlipped(g, assets.kitten(color), x, y, t, facingLeft);

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

    if (Buff.CATNIP.in(buffs)) { // three green motes circling: the frenzy
      for (int i = 0; i < 3; i++) {
        double a = clock * 5 + i * Math.PI * 2 / 3;
        int sx = Math.round(at.x + (float) Math.cos(a) * t * 0.62f);
        int sy = Math.round(at.y + (float) Math.sin(a) * t * 0.40f);
        g.setColor(Theme.alpha(Theme.POWER_CATNIP, 0.9f));
        g.fillOval(sx - 2, sy - 2, 5, 5);
      }
    }
    if (Buff.SHIELD.in(buffs)) { // a bubble around the kitten
      int r = Math.round(t * 0.72f);
      float shimmer = (float) (0.5 + 0.5 * Math.sin(clock * 4 + id));
      g.setColor(Theme.alpha(Theme.POWER_SHIELD, 0.14f + 0.06f * shimmer));
      g.fillOval(Math.round(at.x) - r, Math.round(at.y) - r, r * 2, r * 2);
      g.setStroke(new BasicStroke(1.6f));
      g.setColor(Theme.alpha(Theme.POWER_SHIELD, 0.65f + 0.25f * shimmer));
      g.drawOval(Math.round(at.x) - r, Math.round(at.y) - r, r * 2, r * 2);
      g.setStroke(baseStroke);
    }

    // Remote kittens get a health bar; the local one uses the heart HUD.
    float top = y - 8;
    if (!self) {
      float frac = Math.clamp(state.hp() / (float) GameConfig.PLAYER_MAX_HEALTH, 0f, 1f);
      if (frac < 1f) {
        healthBar(g, x, y - 8, t, frac);
        top = y - 12;
      }
    }
    nameplate(g, name, at.x, top - 2, self ? Theme.ACCENT : Theme.TEXT);
    if (Buff.NINE_LIVES.in(buffs)) { // a spare life: a paw badge just right of the name
      g.setFont(Theme.FONT_WORLD);
      int badgeX = Math.round(at.x + Theme.textWidth(g, Theme.FONT_WORLD, name) / 2f + 9);
      int badgeY = Math.round(top - 6);
      g.setColor(new Color(0, 0, 0, 130));
      g.fillOval(badgeX - 6, badgeY - 6, 12, 12);
      g.setColor(Theme.POWER_LIVES);
      g.fillOval(badgeX - 3, badgeY - 1, 6, 5);
      g.fillOval(badgeX - 4, badgeY - 4, 2, 2);
      g.fillOval(badgeX - 1, badgeY - 5, 2, 2);
      g.fillOval(badgeX + 2, badgeY - 4, 2, 2);
    }
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

  /** Centred name tag on a dark pill. */
  private static void nameplate(Graphics2D g, String tag, float cx, float baselineY, Color color) {
    g.setFont(Theme.FONT_WORLD);
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

  private void drawEffects(Graphics2D g, Effects effects) {
    Composite baseComposite = g.getComposite();
    Stroke baseStroke = g.getStroke();
    for (Effects.Fx fx : effects.active()) {
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
        case SPLINTERS -> { // shards flung outward, tumbling and fading
          Random r = new Random(fx.amount);
          float spread = (1f - t) * 26f;
          for (int i = 0; i < 9; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            float dist = spread * (0.5f + r.nextFloat());
            float sx = fx.pos.x + (float) Math.cos(a) * dist;
            float sy = fx.pos.y + (float) Math.sin(a) * dist + (1f - t) * (1f - t) * 10f; // they fall
            AffineTransform saved = g.getTransform();
            g.translate(sx, sy);
            g.rotate(a + (1f - t) * 6f * (r.nextBoolean() ? 1 : -1));
            g.setColor(Theme.alpha(i % 3 == 0 ? Theme.CRATE_EDGE : Theme.CRATE_WOOD, Math.min(1f, t * 1.6f)));
            g.fillRect(-4, -1, 8, 3);
            g.setTransform(saved);
          }
        }
        case PICKUP -> {
          int r = Math.round(6 + (1f - t) * 18f);
          g.setStroke(new BasicStroke(2.5f));
          g.setColor(Theme.alpha(Theme.pickupTint(fx.item), 0.9f * t));
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
