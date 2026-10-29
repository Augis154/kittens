package kittens.server;

import kittens.common.GameConfig;
import kittens.common.net.EntityKind;

/** Spare rounds for every limited weapon; the sidearm never needs them. */
final class AmmoEffect implements PickupEffect {
  @Override
  public EntityKind kind() {
    return EntityKind.AMMO;
  }

  @Override
  public boolean wanted(ServerPlayer player) {
    return player.arsenal().wantsAmmo();
  }

  @Override
  public void apply(ServerPlayer player) {
    player.arsenal().addReserve(GameConfig.AMMO_PICKUP_SHARE);
  }
}
