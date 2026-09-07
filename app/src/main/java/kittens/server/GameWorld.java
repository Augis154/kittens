package kittens.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.net.Snapshot;

/**
 * The authoritative simulation: every connected player plus the map. One {@link #tick(double)}
 * advances the world by a fixed step; {@link #snapshot()} freezes it for the wire. No sockets or
 * threading policy here — {@code Server} owns those.
 */
final class GameWorld {
  private final TileMap map;
  private final ConcurrentHashMap<Integer, ServerPlayer> players = new ConcurrentHashMap<>();
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
      p.tick(map);
    }
  }

  /**
   * The world as {@code viewerId} should see it: entity states plus the last input seq the server
   * has applied for that viewer, so their client can reconcile its prediction.
   */
  Snapshot snapshotFor(int viewerId) {
    List<EntityState> entities = new ArrayList<>(players.size());
    for (ServerPlayer p : players.values()) {
      entities.add(p.toEntityState());
    }
    ServerPlayer viewer = players.get(viewerId);
    long ackSeq = viewer == null ? -1 : viewer.lastProcessedSeq();
    return new Snapshot(tick.get(), ackSeq, entities);
  }

  String mapId() {
    return GameConfig.MAP_ID;
  }
}
