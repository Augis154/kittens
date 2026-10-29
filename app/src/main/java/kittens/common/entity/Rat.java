package kittens.common.entity;

import kittens.common.GameConfig;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;

/**
 * Concrete prototype for medium enemy: pursues the closest kitten, then attacks and retreats after
 * its first bite (Prototype pattern). Cloned when spawning rats in the arena.
 */
public class Rat extends Enemy {
  public Rat(int id, Vec2 pos) {
    this(id, pos, Vec2.of(GameConfig.RAT_SIZE, GameConfig.RAT_SIZE), GameConfig.RAT_MAX_HEALTH,
        GameConfig.RAT_SPEED, GameConfig.RAT_DAMAGE, GameConfig.RAT_ATTACK_COOLDOWN);
  }

  public Rat(int id, Vec2 pos, Vec2 size, double maxHealth, float speed, double attackDamage,
      double attackCooldownTime) {
    super(id, pos, size, maxHealth, speed, attackDamage, attackCooldownTime,
        EntityKind.RAT, idArg -> new PursuitMovementStrategy());
  }

  public Rat(Rat prototype, int id, Vec2 pos) {
    super(prototype, id, pos);
  }

  @Override
  public Rat clone(int id, Vec2 pos) {
    return new Rat(this, id, pos);
  }

  @Override
  public Rat copy() {
    return new Rat(this, id, pos);
  }

  /** Returns a new prototype with adjusted max health. */
  public Rat withMaxHealth(double newMaxHealth) {
    return new Rat(id, pos, size, newMaxHealth, speed, attackDamage, attackCooldownTime);
  }

  /** Returns a new prototype with adjusted speed. */
  public Rat withSpeed(float newSpeed) {
    return new Rat(id, pos, size, maxHealth, newSpeed, attackDamage, attackCooldownTime);
  }

  /** Returns a new prototype with adjusted attack damage. */
  public Rat withAttackDamage(double newAttackDamage) {
    return new Rat(id, pos, size, maxHealth, speed, newAttackDamage, attackCooldownTime);
  }

  /** Returns a new prototype with adjusted size. */
  public Rat withSize(Vec2 newSize) {
    return new Rat(id, pos, newSize, maxHealth, speed, attackDamage, attackCooldownTime);
  }
}
