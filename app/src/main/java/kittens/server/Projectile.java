package kittens.server;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;

/**
 * A server-simulated bullet: travels in a straight line, dies on a wall, on the first player it
 * hits (dealing damage), or when its lifetime runs out. Purely authoritative — clients just draw
 * whatever the snapshot contains.
 */
final class Projectile {
  private static final Vec2 PLAYER_BOX =
      Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE);

  private final int id;
  private final int ownerId;
  private final Vec2 velocity;
  private final double damage;

  private Vec2 pos;
  private double life = GameConfig.PROJECTILE_LIFETIME;
  private boolean alive = true;

  Projectile(int id, int ownerId, Vec2 pos, float angle) {
    this.id = id;
    this.ownerId = ownerId;
    this.pos = pos;
    this.velocity = Vec2.of((float) Math.cos(angle), (float) Math.sin(angle))
        .scale(GameConfig.PROJECTILE_SPEED);
    this.damage = GameConfig.PROJECTILE_DAMAGE;
  }

  boolean alive() {
    return alive;
  }

  void tick(double dt, TileMap map, Collection<ServerPlayer> players) {
    life -= dt;
    if (life <= 0) {
      alive = false;
      return;
    }
    pos = pos.add(velocity.scale((float) dt));

    if (map.isWallAt(pos)) {
      alive = false;
      return;
    }

    for (ServerPlayer p : players) {
      if (p.id() == ownerId || p.dead()) {
        continue;
      }
      if (!GameConfig.FRIENDLY_FIRE) {
        continue; // no enemies yet, so nothing else to hit
      }
      if (Aabb.fromCenter(p.pos(), PLAYER_BOX).contains(pos)) {
        p.damage(damage);
        alive = false;
        return;
      }
    }
  }

  EntityState toEntityState() {
    float angle = (float) Math.atan2(velocity.y, velocity.x);
    return new EntityState(id, "bullet", pos.x, pos.y, angle, 0f, -1);
  }
}
