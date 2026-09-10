package kittens.server;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;

/**
 * Scatters health and ammo {@link Pickup}s over the level.
 *
 * <p>One drops every {@link #SPAWN_INTERVAL} seconds, up to {@link #MAX_ACTIVE} lying around at
 * once, on a random plain floor tile clear of the players and of the pickups already out there —
 * so supply keeps moving around the map instead of piling up in one room. Combined with the
 * pickups' own expiry, the steady state is a handful of crates that keep relocating.
 *
 * <p>Spawn tiles are deliberately excluded (see {@link TileMap#floorPoints()}): waves arrive on
 * those, so a crate sitting on one would be bait rather than a reward.
 */
final class PickupDirector {
  /** Ceiling on pickups on the floor at once, over the whole map rather than per player. */
  private static final int MAX_ACTIVE = 6;

  private static final double SPAWN_INTERVAL = 7.0;

  /** Grace before the first drop, so the very first wave still has to be fought without help. */
  private static final double FIRST_SPAWN_DELAY = 6.0;

  /**
   * How far a new pickup lands from every living player. A crate materialising under someone's
   * feet is free healing they never went to get; a few tiles of walking is the price.
   */
  private static final float MIN_PLAYER_DIST = 3 * GameConfig.TILE;

  /** Spacing between pickups, so a stockpile can't build up in one corner. */
  private static final float MIN_PICKUP_DIST = 5 * GameConfig.TILE;

  /** Rejection-sampling budget per attempt; a crowded map simply waits for the next interval. */
  private static final int PLACEMENT_ATTEMPTS = 24;

  /** Share of drops that are medkits; the rest are ammo crates. */
  private static final double HEALTH_SHARE = 0.5;

  private final AtomicInteger nextPickupId = new AtomicInteger(GameConfig.PICKUP_ID_BASE);
  private final Random random = new Random();

  private double spawnCooldown = FIRST_SPAWN_DELAY;

  /**
   * Advances the drop timer and appends at most one new pickup to {@code newPickupsOut}. Takes the
   * live pickups as {@code active} for spacing, and never mutates either collection itself.
   */
  void tick(
      double dt,
      TileMap map,
      Collection<ServerPlayer> players,
      Collection<Pickup> active,
      List<Pickup> newPickupsOut) {
    if (players.isEmpty()) {
      return;
    }

    spawnCooldown -= dt;
    if (spawnCooldown > 0 || active.size() >= MAX_ACTIVE) {
      return;
    }

    Vec2 pos = selectPosition(map, players, active);
    if (pos == null) {
      // Nowhere sensible right now — wait out another interval rather than dropping it underfoot.
      spawnCooldown = SPAWN_INTERVAL;
      return;
    }

    Pickup.Kind kind =
        random.nextDouble() < HEALTH_SHARE ? Pickup.Kind.HEALTH : Pickup.Kind.AMMO;
    newPickupsOut.add(new Pickup(nextPickupId.getAndIncrement(), kind, pos));
    spawnCooldown = SPAWN_INTERVAL;
  }

  private Vec2 selectPosition(
      TileMap map, Collection<ServerPlayer> players, Collection<Pickup> active) {
    List<Vec2> floor = map.floorPoints();
    if (floor.isEmpty()) {
      return null;
    }
    for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
      Vec2 candidate = floor.get(random.nextInt(floor.size()));
      if (isClear(candidate, players, active)) {
        return candidate;
      }
    }
    return null;
  }

  private static boolean isClear(
      Vec2 candidate, Collection<ServerPlayer> players, Collection<Pickup> active) {
    for (ServerPlayer p : players) {
      if (!p.dead() && candidate.sub(p.pos()).lengthSq() < MIN_PLAYER_DIST * MIN_PLAYER_DIST) {
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
