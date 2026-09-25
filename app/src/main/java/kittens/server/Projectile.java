package kittens.server;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.entity.Enemy;
import kittens.common.entity.GameObject;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;
import kittens.common.sim.Motion;
import kittens.common.weapon.Weapon;

/**
 * A bullet: straight flight, dies on a wall, the first thing it hits, or when its lifetime ends;
 * explosive rounds then ask {@link GameWorld} for a blast.
 *
 * <p>Collision is <em>swept</em> over the whole segment travelled each tick: at 30 Hz a rifle round
 * covers ~17 px per tick, wider than a mouse, so an endpoint-only test would pass clean through
 * small enemies and over anything touching the shooter.
 */
final class Projectile extends GameObject {
  private static final Vec2 SIZE = Vec2.of(4f, 4f);
  private static final float NO_HIT = Float.POSITIVE_INFINITY;

  private final int ownerId;
  private final Weapon weapon;
  /** Carried so the round can name its weapon on the wire; weapons themselves are id-less. */
  private final int weaponId;
  private final Vec2 velocity;
  private double life;
  private boolean explosionPending;

  Projectile(int id, int ownerId, Vec2 pos, float angle, Weapon weapon, int weaponId) {
    super(id, pos, SIZE);
    this.ownerId = ownerId;
    this.weapon = weapon;
    this.weaponId = weaponId;
    this.velocity = Vec2.fromAngle(angle).scale(weapon.projectileSpeed());
    this.life = weapon.projectileLifetime();
  }

  Weapon weapon() {
    return weapon;
  }

  /** True once, on the tick an explosive round died. */
  boolean consumeExplosion() {
    boolean r = explosionPending;
    explosionPending = false;
    return r;
  }

  void tick(double dt, TileMap map, Collection<ServerPlayer> players, Collection<Enemy> enemies) {
    if (!isAlive()) {
      return;
    }
    Vec2 from = pos;
    // Capped at the life remaining, so range is exactly speed x lifetime and no more.
    pos = pos.add(velocity.scale((float) Math.min(dt, Math.max(0.0, life))));
    Vec2 to = pos;
    life -= dt;

    // Keep only the nearest thing along the segment, wall or body.
    float nearest = firstWallFraction(map, from, to);
    Enemy hitEnemy = null;
    ServerPlayer hitPlayer = null;
    for (Enemy enemy : enemies) {
      float t = enemy.isAlive() ? sweep(from, to, enemy.bounds()) : NO_HIT;
      if (t < nearest) {
        nearest = t;
        hitEnemy = enemy;
      }
    }
    if (GameConfig.FRIENDLY_FIRE) {
      for (ServerPlayer p : players) {
        float t = p.id() != ownerId && p.isAlive() ? sweep(from, to, p.bounds()) : NO_HIT;
        if (t < nearest) {
          nearest = t;
          hitPlayer = p;
          hitEnemy = null;
        }
      }
    }

    if (nearest < NO_HIT) {
      pos = from.add(to.sub(from).scale(nearest)); // stop at the impact point
      if (hitEnemy != null) {
        hitEnemy.damage(weapon.damage());
        hitEnemy.applyKnockback(velocity.normalized().scale(GameConfig.PROJECTILE_KNOCKBACK));
      } else if (hitPlayer != null) {
        hitPlayer.damage(weapon.damage());
      }
      detonate();
    } else if (life <= 0) {
      detonate();
    }
  }

  private void detonate() {
    kill();
    explosionPending = weapon.explosive();
  }

  /**
   * Fraction of {@code from -> to} at which the round enters {@code box}, or {@link #NO_HIT}: a
   * ray/slab test against the box grown by the round's half-size. 0 when the segment starts inside
   * (an enemy pressed against the muzzle).
   */
  private static float sweep(Vec2 from, Vec2 to, Aabb box) {
    float hw = SIZE.x * 0.5f;
    float hh = SIZE.y * 0.5f;
    float enter = 0f;
    float exit = 1f;

    float dx = to.x - from.x;
    if (Math.abs(dx) < 1e-6f) {
      if (from.x < box.minX - hw || from.x > box.maxX + hw) {
        return NO_HIT;
      }
    } else {
      float t1 = (box.minX - hw - from.x) / dx;
      float t2 = (box.maxX + hw - from.x) / dx;
      enter = Math.max(enter, Math.min(t1, t2));
      exit = Math.min(exit, Math.max(t1, t2));
      if (enter > exit) {
        return NO_HIT;
      }
    }

    float dy = to.y - from.y;
    if (Math.abs(dy) < 1e-6f) {
      if (from.y < box.minY - hh || from.y > box.maxY + hh) {
        return NO_HIT;
      }
    } else {
      float t1 = (box.minY - hh - from.y) / dy;
      float t2 = (box.maxY + hh - from.y) / dy;
      enter = Math.max(enter, Math.min(t1, t2));
      exit = Math.min(exit, Math.max(t1, t2));
      if (enter > exit) {
        return NO_HIT;
      }
    }
    return enter;
  }

  /**
   * Fraction of {@code from -> to} at which the round meets a wall, or {@link #NO_HIT}. Sampled every
   * half tile including the start (a round spawned inside a wall dies there), then bisected so a
   * blast centre never rests inside the geometry.
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
        return 0f;
      }
      return Motion.largestClear(clear, t, f -> map.isWallAt(from.add(delta.scale((float) f))));
    }
    return NO_HIT;
  }

  EntityState toEntityState() {
    float angle = (float) Math.atan2(velocity.y, velocity.x);
    return new EntityState(id, EntityKind.BULLET, pos.x, pos.y, angle, 0f, weaponId, 0f);
  }
}
