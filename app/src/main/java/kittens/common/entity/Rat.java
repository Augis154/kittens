package kittens.common.entity;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.sim.PathField;

/**
 * Standard chasing enemy: medium health, medium speed, steady pursuit of the closest kitten —
 * charging straight in when it can see one, and following the path field around cover when it can't.
 */
public class Rat extends Enemy {
  public Rat(int id, Vec2 pos) {
    super(
        id,
        pos,
        Vec2.of(GameConfig.RAT_SIZE, GameConfig.RAT_SIZE),
        GameConfig.RAT_MAX_HEALTH,
        GameConfig.RAT_SPEED,
        GameConfig.RAT_DAMAGE,
        GameConfig.RAT_ATTACK_COOLDOWN,
        "rat");
  }

  @Override
  protected Vec2 computeMoveDirection(
      TileMap map, PathField pursuit, Collection<? extends Actor> targets, double dt) {
    Actor closest = findClosestTarget(targets);
    if (closest == null) {
      return Vec2.ZERO;
    }
    return steerToward(map, pursuit, closest.pos());
  }
}
