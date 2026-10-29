package kittens.server;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;

/** What a broken crate leaves in the open: shows its effect, and expires if nobody wants it. */
final class Loot extends Pickup {
  private static final Vec2 SIZE = Vec2.of(GameConfig.PICKUP_SIZE, GameConfig.PICKUP_SIZE);

  private double life = GameConfig.LOOT_LIFETIME;

  Loot(int id, Vec2 pos, PickupEffect effect) {
    super(id, pos, SIZE, effect);
  }

  @Override
  void tick(double dt, TileMap map, Collection<ServerPlayer> players) {
    life -= dt;
    if (life <= 0) {
      kill();
    }
  }

  /** {@code hp} carries the seconds of life left, so the client can blink it before it goes. */
  @Override
  EntityState toEntityState() {
    return new EntityState(id, effect.kind(), pos.x, pos.y, 0f, (float) Math.max(0.0, life), -1,
        0f, 0);
  }
}
