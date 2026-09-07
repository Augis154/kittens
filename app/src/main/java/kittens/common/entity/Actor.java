package kittens.common.entity;

import kittens.common.math.Vec2;

/** A moving, damageable game object: base for players and enemies. */
public class Actor extends GameObject {
  protected Vec2 velocity = Vec2.ZERO;
  protected double health;
  protected double maxHealth;
  protected double facing;

  public Actor(int id, Vec2 pos, Vec2 size, double maxHealth) {
    super(id, pos, size);
    this.maxHealth = maxHealth;
    this.health = maxHealth;
  }

  @Override
  public void update(double dt) {
    pos = pos.add(velocity.scale((float) dt));
  }

  /** Apply damage; clamps health at 0 and kills the actor when it reaches 0. */
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

  /** Restore health, clamped at {@link #maxHealth}. */
  public void heal(double amount) {
    if (amount <= 0) {
      return;
    }
    health = Math.min(maxHealth, health + amount);
  }

  public boolean isDead() {
    return health <= 0 || !alive;
  }

  public Vec2 velocity() {
    return velocity;
  }

  public void setVelocity(Vec2 velocity) {
    this.velocity = velocity;
  }

  public double health() {
    return health;
  }

  public double maxHealth() {
    return maxHealth;
  }

  public double facing() {
    return facing;
  }

  public void setFacing(double facing) {
    this.facing = facing;
  }
}
