package kittens.client;

import java.awt.Graphics2D;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;

/**
 * The window's view onto the world: a fixed-size viewport that follows a target and stays clamped
 * inside the map, so a level may be larger than the screen.
 *
 * <p>This class owns the world/screen conversion in <em>both</em> directions, and callers must use
 * it rather than reimplementing the arithmetic. Anything drawn after {@link #apply} is in world
 * coordinates; anything that starts from a mouse event has to come back through {@link #worldX} /
 * {@link #worldY} <em>every frame</em>, because panning moves the world underneath a cursor that
 * never moved.
 *
 * <p>Client-only, like {@link Theme} — none of this is simulated, so nothing here belongs in
 * {@code GameConfig}.
 */
final class Camera {
  /** Viewport size in tiles. The window is this, times the tile size, times the render scale. */
  private static final int VIEW_TILES_X = 25;
  private static final int VIEW_TILES_Y = 15;

  /** Fraction of the remaining gap the view closes per second while easing. */
  private static final double FOLLOW_RATE = 12.0;

  /**
   * Gap (world px) above which the view jumps instead of easing. A respawn moves the kitten across
   * the level, and panning the whole way would leave the player watching scenery.
   */
  private static final float SNAP_DISTANCE = 420f;

  private final TileMap map;
  private final float scale;
  private final float viewWidth;
  private final float viewHeight;

  /** World coordinates of the viewport's top-left corner. */
  private float x;
  private float y;
  private boolean placed;

  Camera(TileMap map, float scale) {
    this.map = map;
    this.scale = scale;
    this.viewWidth = VIEW_TILES_X * GameConfig.TILE;
    this.viewHeight = VIEW_TILES_Y * GameConfig.TILE;
    this.x = clampX(0f);
    this.y = clampY(0f);
  }

  int windowWidth() {
    return Math.round(viewWidth * scale);
  }

  int windowHeight() {
    return Math.round(viewHeight * scale);
  }

  /** Eases the viewport toward centring {@code target}, or jumps to it on the first call. */
  void follow(Vec2 target, double dt) {
    if (target == null) {
      return;
    }
    float goalX = clampX(target.x - viewWidth * 0.5f);
    float goalY = clampY(target.y - viewHeight * 0.5f);

    float dx = goalX - x;
    float dy = goalY - y;
    if (!placed || dx * dx + dy * dy > SNAP_DISTANCE * SNAP_DISTANCE || dt <= 0) {
      x = goalX;
      y = goalY;
      placed = true;
      return;
    }
    // Exponential ease, framed in elapsed time so the feel does not change with the frame rate.
    float k = (float) (1.0 - Math.exp(-FOLLOW_RATE * dt));
    x += dx * k;
    y += dy * k;
  }

  /**
   * Scales and offsets {@code g} so that world coordinates land in the window. The offset is
   * snapped to whole screen pixels: the sprites are drawn nearest-neighbour, and a fractional pan
   * would make them shimmer as the camera creeps.
   */
  void apply(Graphics2D g) {
    g.translate(-offsetX(), -offsetY());
    g.scale(scale, scale);
  }

  /** Inverse of {@link #apply} for the x axis — screen pixels back to world coordinates. */
  float worldX(int screenX) {
    return (screenX + offsetX()) / scale;
  }

  float worldY(int screenY) {
    return (screenY + offsetY()) / scale;
  }

  /**
   * The block of tiles the viewport can currently see, widened by one so that a tile straddling the
   * edge — and the shadow a wall casts below itself — is still drawn.
   */
  Renderer.Tiles visibleTiles() {
    int tile = GameConfig.TILE;
    return new Renderer.Tiles(
        Math.max(0, (int) Math.floor(x / tile) - 1),
        Math.max(0, (int) Math.floor(y / tile) - 1),
        Math.min(map.width() - 1, (int) Math.floor((x + viewWidth) / tile) + 1),
        Math.min(map.height() - 1, (int) Math.floor((y + viewHeight) / tile) + 1));
  }

  private int offsetX() {
    return Math.round(x * scale);
  }

  private int offsetY() {
    return Math.round(y * scale);
  }

  /**
   * Keeps the viewport inside the map. A map narrower than the view is centred instead, which is
   * why the original single-screen arena still renders exactly as it did before the camera existed.
   */
  private float clampX(float desired) {
    float max = map.pixelWidth() - viewWidth;
    return max <= 0 ? max * 0.5f : Math.clamp(desired, 0f, max);
  }

  private float clampY(float desired) {
    float max = map.pixelHeight() - viewHeight;
    return max <= 0 ? max * 0.5f : Math.clamp(desired, 0f, max);
  }
}
