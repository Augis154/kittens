package kittens.server;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.entity.Enemy;
import kittens.common.entity.GameObject;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;

/**
 * A server-simulated bullet: travels in a straight line, dies on a wall, on the first enemy or
 * player it hits (dealing damage and, to enemies, knockback), or when its lifetime runs out.
 * Purely authoritative — clients just draw whatever the snapshot contains. Speed, damage and
 * lifetime come from the firing {@link kittens.common.weapon.Weapon}.
 */
final class Projectile extends GameObject {
  private static final Vec2 BULLET_SIZE = Vec2.of(4f, 4f);
  private static final Vec2 PLAYER_BOX =
      Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE);

  private final int ownerId;
  private final int weaponId;
  private final Vec2 velocity;
  private final double damage;
  private double life;

  Projectile(int id, int ownerId, int weaponId, Vec2 pos, float angle,
      double damage, float speed, double lifetime) {
    super(id, pos, BULLET_SIZE);
    this.ownerId = ownerId;
    this.weaponId = weaponId;
    this.velocity =
        Vec2.of((float) Math.cos(angle), (float) Math.sin(angle)).scale(speed);
    this.damage = damage;
    this.life = lifetime;
  }

  boolean alive() {
    return isAlive();
  }

  @Override
  public void update(double dt) {
    life -= dt;
    if (life <= 0) {
      kill();
      return;
    }
    pos = pos.add(velocity.scale((float) dt));
  }

  void tick(
      double dt,
      TileMap map,
      Collection<ServerPlayer> players,
      Collection<Enemy> enemies) {
    update(dt);
    if (!isAlive()) {
      return;
    }

    if (map.isWallAt(pos)) {
      kill();
      return;
    }

    // Check hit against enemies.
    if (enemies != null) {
      for (Enemy enemy : enemies) {
        if (!enemy.isAlive()) {
          continue;
        }
        if (enemy.bounds().contains(pos)) {
          enemy.damage(damage);
          enemy.applyKnockback(velocity.normalized().scale(GameConfig.PROJECTILE_KNOCKBACK));
          kill();
          return;
        }
      }
    }

    // Check hit against players.
    if (players != null) {
      for (ServerPlayer p : players) {
        if (p.id() == ownerId || p.dead()) {
          continue;
        }
        if (!GameConfig.FRIENDLY_FIRE) {
          continue;
        }
        if (Aabb.fromCenter(p.pos(), PLAYER_BOX).contains(pos)) {
          p.damage(damage);
          kill();
          return;
        }
      }
    }
  }

  EntityState toEntityState() {
    float angle = (float) Math.atan2(velocity.y, velocity.x);
    return new EntityState(id, "bullet", pos.x, pos.y, angle, 0f, weaponId);
  }
}
