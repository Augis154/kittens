package kittens.server;

import kittens.common.net.EntityKind;

/**
 * What a pickup does to the kitten who takes it: the Implementor side of the pickups' Bridge.
 * {@link Pickup} and its subclasses decide <em>how</em> an effect reaches a kitten (a crate that
 * has to be shot open, the loot it drops, an enemy's drop); this decides <em>what</em> it does.
 * Either side can grow without touching the other: a new effect fits every delivery, a new delivery
 * carries every effect. Effects hold no state, so one instance can sit in any number of pickups.
 */
interface PickupEffect {
  /** How the item shows on the wire once it is in the open. */
  EntityKind kind();

  /** Whether taking it would do anything; a pickup that would not is left on the floor. */
  boolean wanted(ServerPlayer player);

  void apply(ServerPlayer player);
}
