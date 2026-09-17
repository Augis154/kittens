package kittens.common.math;

/** Axis-aligned bounding box in float coordinates. */
public final class Aabb {
  public final float minX, minY, maxX, maxY;

  public Aabb(float minX, float minY, float maxX, float maxY) {
    this.minX = minX;
    this.minY = minY;
    this.maxX = maxX;
    this.maxY = maxY;
  }

  /** Box centred on {@code center} with the given full width/height. */
  public static Aabb fromCenter(Vec2 center, Vec2 size) {
    float hw = size.x * 0.5f;
    float hh = size.y * 0.5f;
    return new Aabb(center.x - hw, center.y - hh, center.x + hw, center.y + hh);
  }

  public boolean intersects(Aabb o) {
    return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY;
  }

  @Override
  public String toString() {
    return "Aabb(min=" + minX + ", " + minY + ", max=" + maxX + ", " + maxY + ")";
  }
}
