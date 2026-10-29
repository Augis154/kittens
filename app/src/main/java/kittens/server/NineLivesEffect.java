package kittens.server;

import kittens.common.net.EntityKind;

/** A spare life: the next hit that would down the kitten leaves it standing instead. One at a time. */
final class NineLivesEffect implements PickupEffect {
  @Override
  public EntityKind kind() {
    return EntityKind.NINE_LIVES;
  }

  @Override
  public boolean wanted(ServerPlayer player) {
    return !player.hasNineLives();
  }

  @Override
  public void apply(ServerPlayer player) {
    player.grantNineLives();
  }
}
