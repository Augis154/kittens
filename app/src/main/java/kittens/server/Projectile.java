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
 *
 * <p>Collision is <em>swept</em>: each tick tests the whole segment the round travels, not just
 * where it ends up. At 30 Hz a rifle round covers 17 px per tick — wider than a mouse — so an
 * endpoint-only test let rounds pass clean through small enemies, and let a round spawned at the
 * muzzle step straight over anything already touching the shooter.
 */
final class Projectile extends GameObject {
  private static final Vec2 BULLET_SIZE = Vec2.of(4f, 4f);
  private static final Vec2 PLAYER_BOX =
      Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE);

  /** Returned by the sweep tests when nothing is struck along the segment. */
  private static final float NO_HIT = Float.POSITIVE_INFINITY;

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
    pos = pos.add(velocity.scale((float) dt));
  }

  void tick(
      double dt,
      TileMap map,
      Collection<ServerPlayer> players,
      Collection<Enemy> enemies) {
    if (!isAlive()) {
      return;
    }

    Vec2 from = pos;
    // Cap the step at the life remaining, so a round covers exactly its speed x lifetime and no
    // more — advancing a whole tick first would quietly stretch every weapon's range.
    update(Math.min(dt, Math.max(0.0, life)));
    Vec2 to = pos;
    life -= dt;

    // Walk the whole segment and keep only the nearest thing along it, so a round that could reach
    // both a wall and an enemy this tick resolves against whichever comes first.
    float nearest = firstWallFraction(map, from, to);
    Enemy hitEnemy = null;
    ServerPlayer hitPlayer = null;

    if (enemies != null) {
      for (Enemy enemy : enemies) {
        if (!enemy.isAlive()) {
          continue;
        }
        float t = sweep(from, to, enemy.bounds());
        if (t < nearest) {
          nearest = t;
          hitEnemy = enemy;
        }
      }
    }

    if (GameConfig.FRIENDLY_FIRE && players != null) {
      for (ServerPlayer p : players) {
        if (p.id() == ownerId || p.dead()) {
          continue;
        }
        float t = sweep(from, to, Aabb.fromCenter(p.pos(), PLAYER_BOX));
        if (t < nearest) {
          nearest = t;
          hitPlayer = p;
          hitEnemy = null;
        }
      }
    }

    if (nearest < NO_HIT) {
      pos = from.add(to.sub(from).scale(nearest)); // stop at the impact point, not past it
      if (hitEnemy != null) {
        hitEnemy.damage(damage);
        hitEnemy.applyKnockback(velocity.normalized().scale(GameConfig.PROJECTILE_KNOCKBACK));
      } else if (hitPlayer != null) {
        hitPlayer.damage(damage);
      }
      detonate();
      return;
    }

    if (life <= 0) {
      detonate();
    }
  }

  /**
   * The fraction of {@code from -> to} at which the round first enters {@code box}, or
   * {@link #NO_HIT}. The box is grown by the round's half-size first, which reduces the swept-box
   * problem to a ray/slab test against one enlarged box.
   *
   * <p>Returns {@code 0} when the segment <em>starts</em> inside the box — that is the point-blank
   * case, where an enemy is already pressed against the shooter and overlapping the muzzle.
   */
  private static float sweep(Vec2 from, Vec2 to, Aabb box) {
    float halfWidth = BULLET_SIZE.x * 0.5f;
    float halfHeight = BULLET_SIZE.y * 0.5f;
    float enter = 0f;
    float exit = 1f;

    float dx = to.x - from.x;
    if (Math.abs(dx) < 1e-6f) {
      if (from.x < box.minX - halfWidth || from.x > box.maxX + halfWidth) {
        return NO_HIT;
      }
    } else {
      float t1 = (box.minX - halfWidth - from.x) / dx;
      float t2 = (box.maxX + halfWidth - from.x) / dx;
      enter = Math.max(enter, Math.min(t1, t2));
      exit = Math.min(exit, Math.max(t1, t2));
      if (enter > exit) {
        return NO_HIT;
      }
    }

    float dy = to.y - from.y;
    if (Math.abs(dy) < 1e-6f) {
      if (from.y < box.minY - halfHeight || from.y > box.maxY + halfHeight) {
        return NO_HIT;
      }
    } else {
      float t1 = (box.minY - halfHeight - from.y) / dy;
      float t2 = (box.maxY + halfHeight - from.y) / dy;
      enter = Math.max(enter, Math.min(t1, t2));
      exit = Math.min(exit, Math.max(t1, t2));
      if (enter > exit) {
        return NO_HIT;
      }
    }

    return enter;
  }

  /**
   * The fraction of {@code from -> to} at which the round first meets a blocking tile, or
   * {@link #NO_HIT}. Sampled every half tile, including the start: a round covers well under a tile
   * per tick, so this cannot skip a wall, and testing the start means a round spawned inside one
   * dies there instead of flying through it.
   */
  private static float firstWallFraction(TileMap map, Vec2 from, Vec2 to) {
    Vec2 delta = to.sub(from);
    int samples = Math.max(1, (int) Math.ceil(delta.length() / (map.tileSize() * 0.5f)));
    float clear = 0f;
    for (int i = 0; i <= samples; i++) {
      float t = (float) i / samples;
      if (!map.isWallAt(from.add(delta.scale(t)))) {
        clear = t;
        continue;
      }
      if (i == 0) {
        return 0f; // spawned inside a wall — die on the spot
      }
      // Narrow the bracket between the last clear sample and this blocked one, the same way
      // PlayerMotion lands an actor flush against a wall. Without it a round — and so a bazooka's
      // blast centre — could come to rest up to half a tile inside the geometry.
      float blocked = t;
      for (int step = 0; step < 6; step++) {
        float mid = (clear + blocked) * 0.5f;
        if (map.isWallAt(from.add(delta.scale(mid)))) {
          blocked = mid;
        } else {
          clear = mid;
        }
      }
      return clear;
    }
    return NO_HIT;
  }

  EntityState toEntityState() {
    float angle = (float) Math.atan2(velocity.y, velocity.x);
    return new EntityState(id, "bullet", pos.x, pos.y, angle, 0f, weaponId, 0f);
  }
}
