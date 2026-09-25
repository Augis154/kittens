package kittens.server;

import kittens.common.GameConfig;
import kittens.common.entity.GameObject;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;

/**
 * A medkit or ammo crate on the floor. Expires on its own timer and is consumed by the first player
 * who touches it <em>and would benefit</em>; it dies the moment it is taken so two players can't
 * share one. On the wire {@code hp} carries the seconds of life left.
 */
final class Pickup extends GameObject {
  private static final Vec2 SIZE = Vec2.of(GameConfig.PICKUP_SIZE, GameConfig.PICKUP_SIZE);

  private final EntityKind kind; // HEALTH or AMMO
  private double life = GameConfig.PICKUP_LIFETIME;

  Pickup(int id, EntityKind kind, Vec2 pos) {
    super(id, pos, SIZE);
    this.kind = kind;
  }

  void tick(double dt) {
    life -= dt;
    if (life <= 0) {
      kill();
    }
  }

  boolean tryCollect(ServerPlayer player) {
    if (!isAlive() || !player.isAlive() || !bounds().intersects(player.bounds())) {
      return false;
    }
    if (kind == EntityKind.HEALTH) {
      if (!player.wantsHealth()) {
        return false;
      }
      player.heal(GameConfig.PICKUP_HEALTH_AMOUNT);
    } else {
      if (!player.arsenal().wantsAmmo()) {
        return false;
      }
      player.arsenal().restock();
    }
    kill();
    return true;
  }

  EntityState toEntityState() {
    return new EntityState(id, kind, pos.x, pos.y, 0f, (float) Math.max(0.0, life), -1, 0f);
  }
}
