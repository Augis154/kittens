package kittens.common.entity;

import kittens.common.GameConfig;
import kittens.common.math.Vec2;

/** A damageable object with a facing and a decaying knockback impulse: base of players and enemies. */
public class Actor extends GameObject {
  protected final double maxHealth;
  protected double health;
  protected double facing;
  protected Vec2 knockback = Vec2.ZERO;

  public Actor(int id, Vec2 pos, Vec2 size, double maxHealth) {
    super(id, pos, size);
    this.maxHealth = maxHealth;
    this.health = maxHealth;
  }

  /** Clamps health at 0 and kills the actor when it gets there. */
  public void damage(double amount) {
    if (amount <= 0) {
      return;
    }
    health -= amount;
    if (health <= 0) {
      health = 0;
      kill();
    }
  }

  public void heal(double amount) {
    if (amount > 0) {
      health = Math.min(maxHealth, health + amount);
    }
  }

  /** Adds a shove in px/s that {@link #decayKnockback} bleeds off over the next few ticks. */
  public void applyKnockback(Vec2 force) {
    knockback = knockback.add(force);
  }

  protected void decayKnockback(double dt) {
    knockback = knockback.scale((float) Math.max(0, 1.0 - dt * GameConfig.KNOCKBACK_DECAY));
  }

  public double health() {
    return health;
  }

  public double facing() {
    return facing;
  }
}
