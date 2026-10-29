package kittens.server;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;

/**
 * A wooden crate that has to be shot open. Its contents travel as a plain {@link EntityKind#CRATE},
 * so nobody knows what is inside until it breaks and {@link #release}s them as {@link Loot}. It
 * stops bullets but not bodies: blocking movement would need the client's predictor to know about
 * crates too, and a crate broken a tick earlier on the server would then rubber-band whoever stood
 * behind it.
 */
final class SupplyCrate extends Pickup {
  private static final Vec2 SIZE = Vec2.of(GameConfig.CRATE_SIZE, GameConfig.CRATE_SIZE);

  private double health = GameConfig.CRATE_HEALTH;

  SupplyCrate(int id, Vec2 pos, PickupEffect contents) {
    super(id, pos, SIZE, contents);
  }

  /** Crates wait to be found; they never expire. */
  @Override
  void tick(double dt, TileMap map, Collection<ServerPlayer> players) {}

  /** Walking into a crate does nothing; it has to be broken first. */
  @Override
  boolean tryCollect(ServerPlayer player) {
    return false;
  }

  @Override
  boolean shootable() {
    return true;
  }

  @Override
  void takeHit(double damage) {
    if (!isAlive() || damage <= 0) {
      return;
    }
    health -= damage;
    if (health <= 0) {
      health = 0;
      kill();
    }
  }

  @Override
  Pickup release(int id) {
    return health <= 0 ? new Loot(id, pos, effect) : null;
  }

  /** {@code hp} is the crate's real health, so the client can crack it as it takes hits. */
  @Override
  EntityState toEntityState() {
    return new EntityState(id, EntityKind.CRATE, pos.x, pos.y, 0f, (float) health, -1, 0f, 0);
  }
}
