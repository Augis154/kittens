package kittens.common.math;

import java.util.Objects;

/** Immutable 2D vector of floats. */
public final class Vec2 {
  public static final Vec2 ZERO = new Vec2(0f, 0f);

  private static final float EPSILON = 1e-6f;

  public final float x, y;

  public Vec2(float x, float y) {
    this.x = x;
    this.y = y;
  }

  public static Vec2 of(float x, float y) {
    return new Vec2(x, y);
  }

  public Vec2 add(Vec2 o) {
    return new Vec2(x + o.x, y + o.y);
  }

  public Vec2 sub(Vec2 o) {
    return new Vec2(x - o.x, y - o.y);
  }

  public Vec2 scale(float s) {
    return new Vec2(x * s, y * s);
  }

  public Vec2 withX(float nx) {
    return new Vec2(nx, y);
  }

  public Vec2 withY(float ny) {
    return new Vec2(x, ny);
  }

  /** Unit-length copy, or {@link #ZERO} if this vector is ~zero length. */
  public Vec2 normalized() {
    float len = length();
    if (len < EPSILON) {
      return ZERO;
    }
    return new Vec2(x / len, y / len);
  }

  public float dot(Vec2 o) {
    return x * o.x + y * o.y;
  }

  public float length() {
    return (float) Math.sqrt(lengthSq());
  }

  public float lengthSq() {
    return x * x + y * y;
  }

  public float distance(Vec2 o) {
    return sub(o).length();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Vec2 other)) {
      return false;
    }
    return Float.compare(x, other.x) == 0 && Float.compare(y, other.y) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, y);
  }

  @Override
  public String toString() {
    return "Vec2(" + x + ", " + y + ")";
  }
}
