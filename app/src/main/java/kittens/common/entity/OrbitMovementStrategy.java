package kittens.common.entity;

import java.util.Collection;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.sim.PathField;

/** Approaches the player, then moves around them. */
public final class OrbitMovementStrategy implements MovementStrategy {

  @Override
  public Vec2 direction(Enemy enemy, TileMap map, PathField pursuit,
                        Collection<? extends Actor> targets, double dt) {

    Actor closest = enemy.findClosestTarget(targets);

    if (closest == null) {
      return Vec2.ZERO;
    }

    Vec2 toward = closest.pos().sub(enemy.pos());

    // If far away, move toward the player.
    if (toward.length() > 60f) {
      return enemy.steerToward(map, pursuit, closest.pos());
    }

    // If close, move sideways around the player.
    Vec2 direction = toward.normalized();
    return Vec2.of(-direction.y, direction.x);
  }
}