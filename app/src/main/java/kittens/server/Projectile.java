package kittens.server;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.entity.Enemy;
import kittens.common.entity.GameObject;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.weapon.Weapon;

/**
 * A server-simulated bullet: travels in a straight line, dies on a wall, on the first enemy or
 * player it hits (dealing damage and, to enemies, knockback), or when its lifetime runs out.
 * Explosive weapons (the bazooka) additionally request an area-of-effect blast from
 * {@link GameWorld} when they die. Purely authoritative — clients draw whatever the snapshot
 * contains.
 */
final class Projectile extends GameObject {
  private static final Vec2 BULLET_SIZE = Vec2.of(4f, 4f);
  private static final Vec2 PLAYER_BOX =
      Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE);

  private final int ownerId;
  private final int weaponId;
  private final Vec2 velocity;
  private final double damage;
  private final float explosionRadius;
  private final double explosionDamage;

  private double life;
  private boolean explosionPending;

  Projectile(int id, int ownerId, Vec2 pos, float angle, Weapon weapon) {
    super(id, pos, BULLET_SIZE);
    this.ownerId = ownerId;
    this.weaponId = weapon.id();
    this.velocity = Vec2.of((float) Math.cos(angle), (float) Math.sin(angle))
        .scale(weapon.projectileSpeed);
    this.damage = weapon.damage;
    this.explosionRadius = weapon.explosionRadius;
    this.explosionDamage = weapon.explosionDamage;
    this.life = weapon.projectileLifetime;
  }

  boolean alive() {
    return isAlive();
  }

  int ownerId() {
    return ownerId;
  }

  float explosionRadius() {
    return explosionRadius;
  }

  double explosionDamage() {
    return explosionDamage;
  }

  /** True (once) if this projectile just died and should spawn an AOE blast. */
  boolean consumeExplosion() {
    if (!explosionPending) {
      return false;
    }
    explosionPending = false;
    return true;
  }

  private void detonate() {
    kill();
    if (explosionRadius > 0f) {
      explosionPending = true;
    }
  }

  @Override
  public void update(double dt) {
    life -= dt;
    if (life <= 0) {
      detonate();
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
      detonate();
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
          detonate();
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
          detonate();
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
