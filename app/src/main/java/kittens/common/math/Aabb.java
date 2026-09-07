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

  /** Box whose top-left corner is {@code topLeft}, extending by {@code size}. */
  public static Aabb fromTopLeft(Vec2 topLeft, Vec2 size) {
    return new Aabb(topLeft.x, topLeft.y, topLeft.x + size.x, topLeft.y + size.y);
  }

  public boolean intersects(Aabb o) {
    return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY;
  }

  public boolean contains(Vec2 p) {
    return p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY;
  }

  public Vec2 center() {
    return new Vec2((minX + maxX) * 0.5f, (minY + maxY) * 0.5f);
  }

  public Vec2 size() {
    return new Vec2(maxX - minX, maxY - minY);
  }

  public Aabb translate(Vec2 d) {
    return new Aabb(minX + d.x, minY + d.y, maxX + d.x, maxY + d.y);
  }

  @Override
  public String toString() {
    return "Aabb(min=" + minX + ", " + minY + ", max=" + maxX + ", " + maxY + ")";
  }
}
