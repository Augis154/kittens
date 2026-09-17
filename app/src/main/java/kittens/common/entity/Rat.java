package kittens.common.entity;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.sim.PathField;

/** Medium enemy: steady pursuit of the closest kitten. */
public class Rat extends Enemy {
  public Rat(int id, Vec2 pos) {
    super(id, pos, Vec2.of(GameConfig.RAT_SIZE, GameConfig.RAT_SIZE), GameConfig.RAT_MAX_HEALTH,
        GameConfig.RAT_SPEED, GameConfig.RAT_DAMAGE, GameConfig.RAT_ATTACK_COOLDOWN, EntityKind.RAT);
  }

  @Override
  protected Vec2 computeMoveDirection(TileMap map, PathField pursuit,
      Collection<? extends Actor> targets, double dt) {
    Actor closest = findClosestTarget(targets);
    return closest == null ? Vec2.ZERO : steerToward(map, pursuit, closest.pos());
  }
}
