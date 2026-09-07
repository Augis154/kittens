package kittens.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;
import kittens.common.GameConfig;
import kittens.common.entity.Enemy;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.net.Snapshot;
import kittens.common.weapon.Weapon;

/**
 * The authoritative simulation: connected players, computer-controlled enemies, live projectiles
 * and the map. One {@link #tick(double)} advances the world by a fixed step; {@link
 * #snapshotFor(int)} freezes it for the wire. No sockets or threading policy here — {@code Server}
 * owns those.
 */
final class GameWorld {
  private final TileMap map;
  private final ConcurrentHashMap<Integer, ServerPlayer> players = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Integer, Enemy> enemies = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<Projectile> projectiles = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<Explosion> explosions = new ConcurrentLinkedQueue<>();
  private final AtomicInteger nextProjectileId = new AtomicInteger(GameConfig.PROJECTILE_ID_BASE);
  private final AtomicInteger nextExplosionId = new AtomicInteger(GameConfig.EXPLOSION_ID_BASE);
  private final AtomicLong tick = new AtomicLong();
  private final SpawnDirector spawnDirector = new SpawnDirector();

  GameWorld(TileMap map) {
    this.map = map;
  }

  void addPlayer(int id) {
    List<Vec2> spawns = map.spawnPoints();
    Vec2 spawn = spawns.get(Math.floorMod(id, spawns.size()));
    players.put(id, new ServerPlayer(id, spawn));
  }

  void removePlayer(int id) {
    players.remove(id);
  }

  void applyInput(int id, InputCommand cmd) {
    ServerPlayer p = players.get(id);
    if (p != null) {
      p.acceptInput(cmd);
    }
  }

  void tick(double dt) {
    tick.incrementAndGet();

    // 1. Advance players and handle user inputs
    for (ServerPlayer p : players.values()) {
      p.tick(dt, map);
    }

    // 2. Process player weapon fire
    for (ServerPlayer p : players.values()) {
      if (p.consumeFireRequest()) {
        fire(p);
      }
    }

    // 3. Update spawn director and add newly spawned enemies
    List<Enemy> newEnemies = new ArrayList<>();
    spawnDirector.tick(dt, map, players.values(), enemies.values(), newEnemies);
    for (Enemy enemy : newEnemies) {
      enemies.put(enemy.id(), enemy);
    }

    // 4. Update computer-controlled enemies
    for (Enemy enemy : enemies.values()) {
      enemy.tick(dt, map, players.values(), enemies.values());
    }

    // 5. Update projectiles and check collisions with map, players, and enemies
    for (Projectile pr : projectiles) {
      pr.tick(dt, map, players.values(), enemies.values());
    }

    // 6. Detonate explosive projectiles that just died (bazooka AOE)
    for (Projectile pr : projectiles) {
      if (pr.consumeExplosion()) {
        explode(pr.pos(), pr.explosionRadius(), pr.explosionDamage(), pr.ownerId());
      }
    }

    // 7. Age blast markers, cull dead projectiles / enemies / explosions
    for (Explosion ex : explosions) {
      ex.tick(dt);
    }
    projectiles.removeIf(pr -> !pr.alive());
    enemies.values().removeIf(Enemy::isDead);
    explosions.removeIf(ex -> !ex.alive());
  }

  /** Area-of-effect blast: full damage at the centre, easing to a quarter at the rim. */
  private void explode(Vec2 center, float radius, double peakDamage, int ownerId) {
    float radiusSq = radius * radius;

    for (Enemy enemy : enemies.values()) {
      if (!enemy.isAlive()) {
        continue;
      }
      Vec2 diff = enemy.pos().sub(center);
      float distSq = diff.lengthSq();
      if (distSq <= radiusSq) {
        enemy.damage(peakDamage * falloff(distSq, radius));
        if (distSq > 1e-3f) {
          enemy.applyKnockback(
              diff.normalized().scale(GameConfig.PROJECTILE_KNOCKBACK * 1.6f));
        }
      }
    }

    if (GameConfig.FRIENDLY_FIRE) {
      for (ServerPlayer p : players.values()) {
        if (p.dead()) {
          continue;
        }
        Vec2 diff = p.pos().sub(center);
        float distSq = diff.lengthSq();
        if (distSq <= radiusSq) {
          p.damage(peakDamage * falloff(distSq, radius));
        }
      }
    }

    explosions.add(new Explosion(nextExplosionId.getAndIncrement(), center, radius));
  }

  private static double falloff(float distSq, float radius) {
    float frac = 1f - (float) Math.sqrt(distSq) / radius; // 1 at centre -> 0 at edge
    return Math.max(0.25f, frac);
  }

  private void fire(ServerPlayer shooter) {
    Weapon w = shooter.weapon();
    float base = shooter.aimAngle();
    Vec2 muzzle =
        shooter
            .pos()
            .add(
                Vec2.of((float) Math.cos(base), (float) Math.sin(base))
                    .scale(GameConfig.PLAYER_SIZE * 0.5f + 4f));
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int pellet = 0; pellet < w.pellets; pellet++) {
      float angle =
          w.spread > 0f ? base + (float) rnd.nextDouble(-w.spread, w.spread) : base;
      projectiles.add(
          new Projectile(nextProjectileId.getAndIncrement(), shooter.id(), muzzle, angle, w));
    }
  }

  /**
   * The world as {@code viewerId} should see it: entity states plus the last input seq the server
   * has applied for that viewer, so their client can reconcile its prediction.
   */
  Snapshot snapshotFor(int viewerId) {
    List<EntityState> entities =
        new ArrayList<>(
            players.size() + enemies.size() + projectiles.size() + explosions.size());
    for (ServerPlayer p : players.values()) {
      entities.add(p.toEntityState());
    }
    for (Enemy enemy : enemies.values()) {
      entities.add(enemy.toEntityState());
    }
    for (Projectile pr : projectiles) {
      entities.add(pr.toEntityState());
    }
    for (Explosion ex : explosions) {
      entities.add(ex.toEntityState());
    }
    ServerPlayer viewer = players.get(viewerId);
    long ackSeq = viewer == null ? -1 : viewer.lastProcessedSeq();
    return new Snapshot(tick.get(), ackSeq, entities);
  }

  String mapId() {
    return GameConfig.MAP_ID;
  }
}
