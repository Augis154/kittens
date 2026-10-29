package kittens.common.entity;

import java.util.Collection;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.sim.PathField;

/** Moves toward the player, then retreats for a short time after attacking. */
public final class HitAndRunMovementStrategy implements MovementStrategy {

  private double retreatTime = 0;

  @Override
  public Vec2 direction(Enemy enemy, TileMap map, PathField pursuit,
                        Collection<? extends Actor> targets, double dt) {

    Actor closest = enemy.findClosestTarget(targets);

    if (closest == null) {
      return Vec2.ZERO;
    }

    // Start retreating after an attack.
    if (enemy.attackCooldown > 0 && retreatTime <= 0) {
      retreatTime = 1.5;
    }

    // Retreat from the player.
    if (retreatTime > 0) {
      retreatTime -= dt;
      return enemy.pos().sub(closest.pos()).normalized();
    }

    // Otherwise pursue the player.
    return enemy.steerToward(map, pursuit, closest.pos());
  }
}