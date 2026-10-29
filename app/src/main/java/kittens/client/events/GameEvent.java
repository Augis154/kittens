package kittens.client.events;

import kittens.common.math.Vec2;
import kittens.common.weapon.Weapon;

/**
 * Something that happened in the world, derived on the client by diffing consecutive snapshots.
 * Positions are world pixels. The set is sealed so an observer that switches over it without a
 * default stops compiling the day a new event is added, instead of silently ignoring it.
 */
public sealed interface GameEvent {
  /**
   * A projectile seen for the first time. {@code origin} is where it left the barrel, a tick before
   * {@code at}; {@code slot} is the weapon's position in its own family, not the wire id.
   */
  record ShotFired(Vec2 origin, Vec2 at, float angle, Weapon weapon, int slot) implements GameEvent {}

  /** An enemy lost health. {@code damage} is the whole-number drop since the previous snapshot. */
  record EnemyHit(int enemyId, Vec2 at, int damage) implements GameEvent {}

  /** An enemy left the snapshot; {@code at} is where it was last seen. */
  record EnemyKilled(Vec2 at) implements GameEvent {}

  /** A pickup vanished with life to spare, so somebody walked into it rather than it expiring. */
  record PickupCollected(Vec2 at, boolean health) implements GameEvent {}

  /** A blast, raised once on the first snapshot it appears in. */
  record Exploded(Vec2 at) implements GameEvent {}

  /** The local kitten lost health. Not positional: it is only ever heard by the one it happens to. */
  record PlayerHurt() implements GameEvent {}

  /** The local kitten's reload began. */
  record ReloadStarted() implements GameEvent {}
}
