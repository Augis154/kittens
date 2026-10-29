package kittens.common.entity;

import java.util.Collection;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.sim.PathField;

/** Pursues a player while alternating a sideways steering offset. Use one instance per enemy. */
public final class WeavingMovementStrategy implements MovementStrategy {
  private double weaveTime;

  public WeavingMovementStrategy(int enemyId) {
    weaveTime = enemyId * 1.618; // desynchronise the weave between mice
  }

  @Override
  public Vec2 direction(Enemy enemy, TileMap map, PathField pursuit,
      Collection<? extends Actor> targets, double dt) {
    weaveTime += dt * 6.0;
    Actor closest = enemy.findClosestTarget(targets);
    if (closest == null) {
      return Vec2.ZERO;
    }
    Vec2 dir = enemy.steerToward(map, pursuit, closest.pos());
    if (dir.lengthSq() < 1e-4f) {
      return Vec2.ZERO;
    }
    Vec2 perp = Vec2.of(-dir.y, dir.x);
    return dir.add(perp.scale((float) Math.sin(weaveTime) * 0.45f)).normalized();
  }
}
