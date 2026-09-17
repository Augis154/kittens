package kittens.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import kittens.common.GameConfig;
import kittens.common.entity.Enemy;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.net.Snapshot;
import kittens.common.sim.PathField;
import kittens.common.weapon.Weapon;

/**
 * The authoritative simulation, a Facade over players, enemies, projectiles, pickups and the map.
 * Everything is mutated on the loop thread except {@link #addPlayer}/{@link #removePlayer}, which
 * arrive from reader threads — hence only {@code players} is a concurrent collection.
 */
final class GameWorld {
  private final TileMap map;
  private final ConcurrentHashMap<Integer, ServerPlayer> players = new ConcurrentHashMap<>();
  private final List<Enemy> enemies = new ArrayList<>();
  private final List<Projectile> projectiles = new ArrayList<>();
  private final List<Explosion> explosions = new ArrayList<>();
  private final List<Pickup> pickups = new ArrayList<>();
  private final SpawnDirector spawnDirector = new SpawnDirector();
  private final PickupDirector pickupDirector = new PickupDirector();
  private int nextProjectileId = GameConfig.PROJECTILE_ID_BASE;
  private int nextExplosionId = GameConfig.EXPLOSION_ID_BASE;
  private long tick;

  GameWorld(TileMap map) {
    this.map = map;
  }

  void addPlayer(int id) {
    List<Vec2> spawns = map.spawnPoints();
    players.put(id, new ServerPlayer(id, spawns.get(Math.floorMod(id, spawns.size()))));
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

  /** One fixed step: players, fire, pickups, spawns, enemies, projectiles, blasts, cull. */
  void tick(double dt) {
    tick++;

    for (ServerPlayer p : players.values()) {
      p.tick(dt, map);
    }
    for (ServerPlayer p : players.values()) {
      if (p.consumeFireRequest()) {
        fire(p);
      }
    }

    for (Pickup pk : pickups) {
      pk.tick(dt);
      for (ServerPlayer p : players.values()) {
        if (pk.tryCollect(p)) {
          break;
        }
      }
    }
    Pickup dropped = pickupDirector.tick(dt, map, players.values(), pickups);
    if (dropped != null) {
      pickups.add(dropped);
    }

    Enemy spawned = spawnDirector.tick(dt, map, players.values(), enemies);
    if (spawned != null) {
      enemies.add(spawned);
    }
    if (!enemies.isEmpty()) {
      // One field per tick, shared by every enemy: cheaper than caching it correctly, never stale.
      PathField pursuit = PathField.toward(map, alivePlayerPositions());
      for (Enemy enemy : enemies) {
        enemy.tick(dt, map, pursuit, players.values(), enemies);
      }
    }

    for (Projectile pr : projectiles) {
      pr.tick(dt, map, players.values(), enemies);
    }
    for (Projectile pr : projectiles) {
      if (pr.consumeExplosion()) {
        explode(pr.pos(), pr.weapon());
      }
    }
    for (Explosion ex : explosions) {
      ex.tick(dt);
    }

    projectiles.removeIf(pr -> !pr.isAlive());
    enemies.removeIf(e -> !e.isAlive());
    explosions.removeIf(ex -> !ex.isAlive());
    pickups.removeIf(pk -> !pk.isAlive());
  }

  private List<Vec2> alivePlayerPositions() {
    List<Vec2> goals = new ArrayList<>(players.size());
    for (ServerPlayer p : players.values()) {
      if (p.isAlive()) {
        goals.add(p.pos());
      }
    }
    return goals;
  }

  /** Area blast: full damage at the centre easing to a quarter at the rim, plus outward knockback. */
  private void explode(Vec2 center, Weapon weapon) {
    float radius = weapon.explosionRadius();
    float radiusSq = radius * radius;
    for (Enemy enemy : enemies) {
      Vec2 diff = enemy.pos().sub(center);
      float distSq = diff.lengthSq();
      if (!enemy.isAlive() || distSq > radiusSq) {
        continue;
      }
      enemy.damage(weapon.explosionDamage() * falloff(distSq, radius));
      if (distSq > 1e-3f) {
        enemy.applyKnockback(diff.normalized().scale(GameConfig.PROJECTILE_KNOCKBACK * 1.6f));
      }
    }
    if (GameConfig.FRIENDLY_FIRE) {
      for (ServerPlayer p : players.values()) {
        float distSq = p.pos().sub(center).lengthSq();
        if (p.isAlive() && distSq <= radiusSq) {
          p.damage(weapon.explosionDamage() * falloff(distSq, radius));
        }
      }
    }
    explosions.add(new Explosion(nextExplosionId++, center, radius));
  }

  private static double falloff(float distSq, float radius) {
    return Math.max(0.25f, 1f - (float) Math.sqrt(distSq) / radius);
  }

  private void fire(ServerPlayer shooter) {
    Weapon w = shooter.loadout().weapon();
    float base = shooter.aimAngle();
    Vec2 aim = Vec2.fromAngle(base);
    Vec2 muzzle = shooter.pos().add(aim.scale(GameConfig.PLAYER_SIZE * 0.5f + 4f));
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int pellet = 0; pellet < w.pellets(); pellet++) {
      float angle = w.spread() > 0f ? base + (float) rnd.nextDouble(-w.spread(), w.spread()) : base;
      projectiles.add(new Projectile(nextProjectileId++, shooter.id(), muzzle, angle, w));
    }
    if (w.recoil() > 0f) {
      shooter.applyKnockback(aim.scale(-w.recoil()));
    }
  }

  /** The world as {@code viewerId} sees it: all entities plus that viewer's ack and ammo. */
  Snapshot snapshotFor(int viewerId) {
    List<EntityState> entities = new ArrayList<>(players.size() + enemies.size()
        + projectiles.size() + explosions.size() + pickups.size());
    for (ServerPlayer p : players.values()) {
      entities.add(p.toEntityState());
    }
    for (Enemy enemy : enemies) {
      entities.add(enemy.toEntityState());
    }
    for (Projectile pr : projectiles) {
      entities.add(pr.toEntityState());
    }
    for (Explosion ex : explosions) {
      entities.add(ex.toEntityState());
    }
    for (Pickup pk : pickups) {
      entities.add(pk.toEntityState());
    }
    ServerPlayer viewer = players.get(viewerId);
    if (viewer == null) {
      return new Snapshot(tick, -1, entities, 0, 0f);
    }
    return new Snapshot(tick, viewer.lastProcessedSeq(), entities, viewer.loadout().ammo(),
        viewer.loadout().reloadProgress());
  }
}
