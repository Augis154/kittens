package kittens.server;

import kittens.common.GameConfig;
import kittens.common.net.Buff;
import kittens.common.net.EntityKind;

/** Catnip frenzy: the kitten fires and reloads twice as fast for a while — and burns ammo as fast. */
final class CatnipEffect implements PickupEffect {
  @Override
  public EntityKind kind() {
    return EntityKind.CATNIP;
  }

  @Override
  public boolean wanted(ServerPlayer player) {
    return player.buffLeft(Buff.CATNIP) < GameConfig.CATNIP_SECONDS - 1;
  }

  @Override
  public void apply(ServerPlayer player) {
    player.grant(Buff.CATNIP, GameConfig.CATNIP_SECONDS);
  }
}
