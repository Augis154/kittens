package kittens.server;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;
import kittens.common.GameConfig;
import kittens.common.entity.Enemy;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;

/**
 * Decides what supplies appear and where: a {@link SupplyCrate} with hidden contents every few
 * seconds, up to a cap, on a random plain floor tile clear of the players and of other pickups; and,
 * now and then, an {@link EnemyDrop} where an enemy died. Contents come from weighted tables — crates
 * favour the rarer power-ups, enemy drops the ammo a fight burns through.
 */
final class PickupDirector {
  private static final int MAX_CRATES = 4;
  private static final double SPAWN_INTERVAL = 8.0;
  /** Grace before the first crate, so the first wave is fought without help. */
  private static final double FIRST_SPAWN_DELAY = 6.0;
  /** A crate under someone's feet is a free reward they never went to get. */
  private static final float MIN_PLAYER_DIST = 3 * GameConfig.TILE;
  private static final float MIN_PICKUP_DIST = 5 * GameConfig.TILE;
  /** Rejection-sampling budget; a crowded map just waits for the next interval. */
  private static final int PLACEMENT_ATTEMPTS = 24;
  /** Mice die in droves, so each one drops less often than a rat. */
  private static final double RAT_DROP_CHANCE = 0.14;
  private static final double MOUSE_DROP_CHANCE = 0.07;

  private record Weighted(PickupEffect effect, double weight) {}

  // Effects are stateless, so every pickup carrying one shares the same instance.
  private static final PickupEffect HEAL = new HealEffect();
  private static final PickupEffect AMMO = new AmmoEffect();
  private static final PickupEffect DOUBLE_DAMAGE = new DoubleDamageEffect();
  private static final PickupEffect SHIELD = new ShieldEffect();
  private static final PickupEffect CATNIP = new CatnipEffect();
  private static final PickupEffect NINE_LIVES = new NineLivesEffect();

  private static final List<Weighted> CRATE_TABLE = List.of(
      new Weighted(HEAL, 24), new Weighted(AMMO, 28), new Weighted(DOUBLE_DAMAGE, 13),
      new Weighted(SHIELD, 13), new Weighted(CATNIP, 13), new Weighted(NINE_LIVES, 9));
  private static final List<Weighted> DROP_TABLE = List.of(
      new Weighted(AMMO, 50), new Weighted(HEAL, 28), new Weighted(DOUBLE_DAMAGE, 8),
      new Weighted(CATNIP, 8), new Weighted(SHIELD, 6));

  private final Random random = new Random();
  private double spawnCooldown = FIRST_SPAWN_DELAY;

  /** Advances the crate timer and returns at most one new crate, or {@code null}. */
  SupplyCrate tick(double dt, TileMap map, Collection<ServerPlayer> players,
      List<Pickup> active, IntSupplier ids) {
    if (players.isEmpty()) {
      return null;
    }
    spawnCooldown -= dt;
    if (spawnCooldown > 0 || crates(active) >= MAX_CRATES) {
      return null;
    }
    spawnCooldown = SPAWN_INTERVAL;
    Vec2 pos = selectPosition(map, players, active);
    return pos == null ? null : new SupplyCrate(ids.getAsInt(), pos, pick(CRATE_TABLE));
  }

  /** What {@code enemy} leaves where it died, if anything. */
  EnemyDrop dropFor(Enemy enemy, IntSupplier ids) {
    double chance = enemy.kind() == EntityKind.MOUSE ? MOUSE_DROP_CHANCE : RAT_DROP_CHANCE;
    return random.nextDouble() < chance
        ? new EnemyDrop(ids.getAsInt(), enemy.pos(), pick(DROP_TABLE))
        : null;
  }

  private PickupEffect pick(List<Weighted> table) {
    double total = 0;
    for (Weighted w : table) {
      total += w.weight();
    }
    double roll = random.nextDouble() * total;
    for (Weighted w : table) {
      roll -= w.weight();
      if (roll < 0) {
        return w.effect();
      }
    }
    return table.getLast().effect();
  }

  private static int crates(List<Pickup> active) {
    int n = 0;
    for (Pickup pk : active) {
      if (pk instanceof SupplyCrate) {
        n++;
      }
    }
    return n;
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
