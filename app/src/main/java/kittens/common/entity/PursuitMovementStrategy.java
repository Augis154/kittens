package kittens.common.entity;

import java.util.Collection;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.sim.PathField;

/** Follows the closest living player, routing around walls when needed. */
public final class PursuitMovementStrategy implements MovementStrategy {
  @Override
  public Vec2 direction(Enemy enemy, TileMap map, PathField pursuit,
      Collection<? extends Actor> targets, double dt) {
    Actor closest = enemy.findClosestTarget(targets);
    return closest == null ? Vec2.ZERO : enemy.steerToward(map, pursuit, closest.pos());
  }
}
