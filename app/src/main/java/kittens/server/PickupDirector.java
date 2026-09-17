package kittens.server;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;

/**
 * Drops one health or ammo {@link Pickup} every few seconds, up to a cap, on a random plain floor
 * tile clear of the players and of the crates already out. With the pickups' own expiry the
 * steady state is a handful of crates that keep relocating.
 */
final class PickupDirector {
  private static final int MAX_ACTIVE = 6;
  private static final double SPAWN_INTERVAL = 7.0;
  /** Grace before the first drop, so the first wave is fought without help. */
  private static final double FIRST_SPAWN_DELAY = 6.0;
  /** A crate under someone's feet is free healing they never went to get. */
  private static final float MIN_PLAYER_DIST = 3 * GameConfig.TILE;
  private static final float MIN_PICKUP_DIST = 5 * GameConfig.TILE;
  /** Rejection-sampling budget; a crowded map just waits for the next interval. */
  private static final int PLACEMENT_ATTEMPTS = 24;
  private static final double HEALTH_SHARE = 0.5;

  private final Random random = new Random();
  private int nextPickupId = GameConfig.PICKUP_ID_BASE;
  private double spawnCooldown = FIRST_SPAWN_DELAY;

  /** Advances the drop timer and returns at most one new pickup, or {@code null}. */
  Pickup tick(double dt, TileMap map, Collection<ServerPlayer> players, List<Pickup> active) {
    if (players.isEmpty()) {
      return null;
    }
    spawnCooldown -= dt;
    if (spawnCooldown > 0 || active.size() >= MAX_ACTIVE) {
      return null;
    }
    spawnCooldown = SPAWN_INTERVAL;
    Vec2 pos = selectPosition(map, players, active);
    if (pos == null) {
      return null;
    }
    EntityKind kind = random.nextDouble() < HEALTH_SHARE ? EntityKind.HEALTH : EntityKind.AMMO;
    return new Pickup(nextPickupId++, kind, pos);
  }

  private Vec2 selectPosition(TileMap map, Collection<ServerPlayer> players, List<Pickup> active) {
    List<Vec2> floor = map.floorPoints();
    for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS && !floor.isEmpty(); attempt++) {
      Vec2 candidate = floor.get(random.nextInt(floor.size()));
      if (isClear(candidate, players, active)) {
        return candidate;
      }
    }
    return null;
  }

  private static boolean isClear(Vec2 candidate, Collection<ServerPlayer> players,
      List<Pickup> active) {
    for (ServerPlayer p : players) {
      if (p.isAlive() && candidate.sub(p.pos()).lengthSq() < MIN_PLAYER_DIST * MIN_PLAYER_DIST) {
        return false;
      }
    }
    for (Pickup pk : active) {
      if (candidate.sub(pk.pos()).lengthSq() < MIN_PICKUP_DIST * MIN_PICKUP_DIST) {
        return false;
      }
    }
    return true;
  }
}
