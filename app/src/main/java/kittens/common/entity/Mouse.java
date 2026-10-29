package kittens.common.entity;

import kittens.common.GameConfig;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;

/**
 * Concrete prototype for swarming enemy: starts by weaving and orbits the kitten after its first
 * bite (Prototype pattern). Cloned when spawning mice in the arena.
 */
public class Mouse extends Enemy {
  public Mouse(int id, Vec2 pos) {
    this(id, pos, Vec2.of(GameConfig.MOUSE_SIZE, GameConfig.MOUSE_SIZE),
        GameConfig.MOUSE_MAX_HEALTH, GameConfig.MOUSE_SPEED, GameConfig.MOUSE_DAMAGE,
        GameConfig.MOUSE_ATTACK_COOLDOWN);
  }

  public Mouse(int id, Vec2 pos, Vec2 size, double maxHealth, float speed, double attackDamage,
      double attackCooldownTime) {
    super(id, pos, size, maxHealth, speed, attackDamage, attackCooldownTime,
        EntityKind.MOUSE, WeavingMovementStrategy::new);
  }

  public Mouse(Mouse prototype, int id, Vec2 pos) {
    super(prototype, id, pos);
  }

  @Override
  public Mouse clone(int id, Vec2 pos) {
    return new Mouse(this, id, pos);
  }

  @Override
  public Mouse copy() {
    return new Mouse(this, id, pos);
  }

  /** Returns a new prototype with adjusted max health. */
  public Mouse withMaxHealth(double newMaxHealth) {
    return new Mouse(id, pos, size, newMaxHealth, speed, attackDamage, attackCooldownTime);
  }

  /** Returns a new prototype with adjusted speed. */
  public Mouse withSpeed(float newSpeed) {
    return new Mouse(id, pos, size, maxHealth, newSpeed, attackDamage, attackCooldownTime);
  }

  /** Returns a new prototype with adjusted attack damage. */
  public Mouse withAttackDamage(double newAttackDamage) {
    return new Mouse(id, pos, size, maxHealth, speed, newAttackDamage, attackCooldownTime);
  }

  /** Returns a new prototype with adjusted size. */
  public Mouse withSize(Vec2 newSize) {
    return new Mouse(id, pos, newSize, maxHealth, speed, attackDamage, attackCooldownTime);
  }
}
