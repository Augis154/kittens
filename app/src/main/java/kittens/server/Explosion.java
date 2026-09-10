package kittens.server;

import kittens.common.math.Vec2;
import kittens.common.net.EntityState;

/**
 * A short-lived blast marker. The damage has already been applied by {@link GameWorld}; this only
 * exists so clients can draw the explosion. On the wire it rides an {@link EntityState} of kind
 * {@code "boom"}: {@code angle} carries the current (expanding) radius, {@code hp} the final radius.
 */
final class Explosion {
  private static final double DURATION = 0.28;

  private final int id;
  private final Vec2 pos;
  private final float radius;
  private double life = DURATION;

  Explosion(int id, Vec2 pos, float radius) {
    this.id = id;
    this.pos = pos;
    this.radius = radius;
  }

  void tick(double dt) {
    life -= dt;
  }

  boolean alive() {
    return life > 0;
  }

  EntityState toEntityState() {
    float progress = (float) Math.clamp(1 - life / DURATION, 0.0, 1.0);
    float currentRadius = radius * (0.35f + 0.65f * progress);
    return new EntityState(id, "boom", pos.x, pos.y, currentRadius, radius, -1, 0f);
  }
}
