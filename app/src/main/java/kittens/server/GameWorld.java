package kittens.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.net.Snapshot;

/**
 * The authoritative simulation: connected players, live projectiles and the map. One
 * {@link #tick(double)} advances the world by a fixed step; {@link #snapshotFor(int)} freezes it
 * for the wire. No sockets or threading policy here — {@code Server} owns those.
 */
final class GameWorld {
  private final TileMap map;
  private final ConcurrentHashMap<Integer, ServerPlayer> players = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<Projectile> projectiles = new ConcurrentLinkedQueue<>();
  private final AtomicInteger nextProjectileId = new AtomicInteger(GameConfig.PROJECTILE_ID_BASE);
  private final AtomicLong tick = new AtomicLong();

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

    for (ServerPlayer p : players.values()) {
      p.tick(dt, map);
    }
    for (ServerPlayer p : players.values()) {
      if (p.consumeFireRequest()) {
        fire(p);
      }
    }
    for (Projectile pr : projectiles) {
      pr.tick(dt, map, players.values());
    }
    projectiles.removeIf(pr -> !pr.alive());
  }

  private void fire(ServerPlayer shooter) {
    float angle = shooter.aimAngle();
    Vec2 muzzle = shooter.pos().add(Vec2.of((float) Math.cos(angle), (float) Math.sin(angle))
        .scale(GameConfig.PLAYER_SIZE * 0.5f + 4f));
    projectiles.add(new Projectile(nextProjectileId.getAndIncrement(), shooter.id(), muzzle, angle));
  }

  /**
   * The world as {@code viewerId} should see it: entity states plus the last input seq the server
   * has applied for that viewer, so their client can reconcile its prediction.
   */
  Snapshot snapshotFor(int viewerId) {
    List<EntityState> entities = new ArrayList<>(players.size() + projectiles.size());
    for (ServerPlayer p : players.values()) {
      entities.add(p.toEntityState());
    }
    for (Projectile pr : projectiles) {
      entities.add(pr.toEntityState());
    }
    ServerPlayer viewer = players.get(viewerId);
    long ackSeq = viewer == null ? -1 : viewer.lastProcessedSeq();
    return new Snapshot(tick.get(), ackSeq, entities);
  }

  String mapId() {
    return GameConfig.MAP_ID;
  }
}
