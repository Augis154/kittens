package kittens.server;

import kittens.common.entity.GameObject;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;

/**
 * A short-lived blast marker; the damage was already dealt by {@link GameWorld}. On the wire
 * {@code angle} carries the current (expanding) radius and {@code hp} the final radius.
 */
final class Explosion extends GameObject {
  private static final double DURATION = 0.28;

  private final float radius;
  private double life = DURATION;

  Explosion(int id, Vec2 pos, float radius) {
    super(id, pos, Vec2.of(radius * 2, radius * 2));
    this.radius = radius;
  }

  void tick(double dt) {
    life -= dt;
    if (life <= 0) {
      kill();
    }
  }

  EntityState toEntityState() {
    float progress = (float) Math.clamp(1 - life / DURATION, 0.0, 1.0);
    float current = radius * (0.35f + 0.65f * progress);
    return new EntityState(id, EntityKind.BOOM, pos.x, pos.y, current, radius, -1, 0f);
  }
}
