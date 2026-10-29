package kittens.server;

import kittens.common.GameConfig;
import kittens.common.net.EntityKind;

/** A medkit: some health back, never past full. */
final class HealEffect implements PickupEffect {
  @Override
  public EntityKind kind() {
    return EntityKind.HEALTH;
  }

  @Override
  public boolean wanted(ServerPlayer player) {
    return player.wantsHealth();
  }

  @Override
  public void apply(ServerPlayer player) {
    player.heal(GameConfig.PICKUP_HEALTH_AMOUNT);
  }
}
