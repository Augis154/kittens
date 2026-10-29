package kittens.server;

import kittens.common.GameConfig;
import kittens.common.net.EntityKind;

/** A bubble that soaks up damage before health does, until it pops or times out. */
final class ShieldEffect implements PickupEffect {
  @Override
  public EntityKind kind() {
    return EntityKind.SHIELD;
  }

  @Override
  public boolean wanted(ServerPlayer player) {
    return player.shieldPoints() < GameConfig.SHIELD_POINTS;
  }

  @Override
  public void apply(ServerPlayer player) {
    player.grantShield(GameConfig.SHIELD_POINTS, GameConfig.SHIELD_SECONDS);
  }
}
