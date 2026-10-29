package kittens.common.entity;

import kittens.common.GameConfig;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;

/** Medium enemy: pursues the closest kitten, then attacks and retreats after its first bite. */
public class Rat extends Enemy {
  public Rat(int id, Vec2 pos) {
    super(id, pos, Vec2.of(GameConfig.RAT_SIZE, GameConfig.RAT_SIZE), GameConfig.RAT_MAX_HEALTH,
        GameConfig.RAT_SPEED, GameConfig.RAT_DAMAGE, GameConfig.RAT_ATTACK_COOLDOWN,
        EntityKind.RAT, new PursuitMovementStrategy());
  }
}
