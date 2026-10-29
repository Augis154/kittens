package kittens.common.entity;

import kittens.common.GameConfig;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;

/** Swarming enemy: starts by weaving and orbits the kitten after its first bite. */
public class Mouse extends Enemy {
  public Mouse(int id, Vec2 pos) {
    super(id, pos, Vec2.of(GameConfig.MOUSE_SIZE, GameConfig.MOUSE_SIZE),
        GameConfig.MOUSE_MAX_HEALTH, GameConfig.MOUSE_SPEED, GameConfig.MOUSE_DAMAGE,
        GameConfig.MOUSE_ATTACK_COOLDOWN, EntityKind.MOUSE, new WeavingMovementStrategy(id));
  }
}
