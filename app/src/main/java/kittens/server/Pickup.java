package kittens.server;

import java.util.Collection;
import kittens.common.entity.GameObject;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;

/**
 * Something in the arena that hands a kitten a {@link PickupEffect} — the Abstraction of the
 * pickups' Bridge, with the effect as its Implementor. Subclasses vary <em>how</em> the effect is
 * delivered ({@link SupplyCrate}, {@link Loot}, {@link EnemyDrop}); the effect varies <em>what</em>
 * it does. Any delivery carries any effect, so three deliveries and six effects are nine classes,
 * not eighteen, and {@link GameWorld} treats every pickup through this one interface.
 *
 * <p>A pickup is only taken if its effect would do something: a kitten at full health walks over a
 * medkit. It dies the moment it is taken, so two kittens can never share one.
 */
abstract class Pickup extends GameObject {
  protected final PickupEffect effect;

  protected Pickup(int id, Vec2 pos, Vec2 size, PickupEffect effect) {
    super(id, pos, size);
    this.effect = effect;
  }

  /** One step of whatever the delivery does on its own: expiring, drifting toward a kitten. */
  abstract void tick(double dt, TileMap map, Collection<ServerPlayer> players);

  /** Hands the effect over if the kitten is touching it, alive, and would benefit. */
  boolean tryCollect(ServerPlayer player) {
    if (!isAlive() || !player.isAlive() || !bounds().intersects(player.bounds())
        || !effect.wanted(player)) {
      return false;
    }
    effect.apply(player);
    kill();
    return true;
  }

  /** Whether bullets and blasts stop on it rather than passing over. */
  boolean shootable() {
    return false;
  }

  /** Damage from a bullet or blast; only a {@link #shootable} pickup does anything with it. */
  void takeHit(double damage) {}

  /** What it leaves behind once dead, or {@code null}: a broken crate leaves its contents. */
  Pickup release(int id) {
    return null;
  }

  abstract EntityState toEntityState();
}
