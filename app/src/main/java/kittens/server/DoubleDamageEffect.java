package kittens.server;

import kittens.common.GameConfig;
import kittens.common.net.Buff;
import kittens.common.net.EntityKind;

/** Every round and blast the kitten fires hits twice as hard for a while. */
final class DoubleDamageEffect implements PickupEffect {
  @Override
  public EntityKind kind() {
    return EntityKind.DOUBLE_DAMAGE;
  }

  /** Left alone while a fresh one is still running, so a second crate isn't wasted on a full timer. */
  @Override
  public boolean wanted(ServerPlayer player) {
    return player.buffLeft(Buff.DOUBLE_DAMAGE) < GameConfig.DOUBLE_DAMAGE_SECONDS - 1;
  }

  @Override
  public void apply(ServerPlayer player) {
    player.grant(Buff.DOUBLE_DAMAGE, GameConfig.DOUBLE_DAMAGE_SECONDS);
  }
}
