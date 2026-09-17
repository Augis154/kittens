package kittens.client;

import java.awt.Graphics2D;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;

/**
 * A fixed-size viewport that follows a target and stays clamped inside the map. Owns the
 * world/screen conversion in both directions: draw after {@link #apply}, and bring mouse positions
 * back through {@link #worldX}/{@link #worldY} every frame, since panning moves the world under a
 * cursor that never moved.
 */
final class Camera {
  private static final int VIEW_TILES_X = 25;
  private static final int VIEW_TILES_Y = 15;
  /** Fraction of the remaining gap closed per second while easing. */
  private static final double FOLLOW_RATE = 12.0;
  /** Gap above which the view jumps instead of easing (a respawn across the level). */
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

  /** Eases toward centring {@code target}, jumping on the first call or past {@link #SNAP_DISTANCE}. */
  void follow(Vec2 target, double dt) {
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
    float k = (float) (1.0 - Math.exp(-FOLLOW_RATE * dt)); // frame-rate independent ease
    x += dx * k;
    y += dy * k;
  }

  /** Offsets and scales {@code g} so world coordinates land in the window; the offset is whole pixels so nearest-neighbour sprites don't shimmer. */
  void apply(Graphics2D g) {
    g.translate(-offsetX(), -offsetY());
    g.scale(scale, scale);
  }

  float worldX(int screenX) {
    return (screenX + offsetX()) / scale;
  }

  float worldY(int screenY) {
    return (screenY + offsetY()) / scale;
  }

  /** The visible tile block, widened by one so straddling tiles and wall shadows still draw. */
  Renderer.Tiles visibleTiles() {
    return new Renderer.Tiles(
        Math.max(0, map.colAt(x) - 1),
        Math.max(0, map.rowAt(y) - 1),
        Math.min(map.width() - 1, map.colAt(x + viewWidth) + 1),
        Math.min(map.height() - 1, map.rowAt(y + viewHeight) + 1));
  }

  private int offsetX() {
    return Math.round(x * scale);
  }

  private int offsetY() {
    return Math.round(y * scale);
  }

  /** Inside the map, or centred when the map is narrower than the view. */
  private float clampX(float desired) {
    float max = map.pixelWidth() - viewWidth;
    return max <= 0 ? max * 0.5f : Math.clamp(desired, 0f, max);
  }

  private float clampY(float desired) {
    float max = map.pixelHeight() - viewHeight;
    return max <= 0 ? max * 0.5f : Math.clamp(desired, 0f, max);
  }
}
