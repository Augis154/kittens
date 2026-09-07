package kittens.common.entity;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.sim.PlayerMotion;

/**
 * Abstract computer-controlled enemy base class extending {@link Actor}.
 *
 * <p>The movement decision is encapsulated in the virtual method {@link
 * #computeMoveDirection(TileMap, Collection, double)}, which serves as the seam where specialized
 * AI behaviors and strategy patterns can be attached.
 */
public abstract class Enemy extends Actor {
  protected final String kind;
  protected final float speed;
  protected final double attackDamage;
  protected final double attackCooldownTime;

  protected double attackCooldown;
  protected Vec2 knockback = Vec2.ZERO;

  public Enemy(
      int id,
      Vec2 pos,
      Vec2 size,
      double maxHealth,
      float speed,
      double attackDamage,
      double attackCooldownTime,
      String kind) {
    super(id, pos, size, maxHealth);
    this.speed = speed;
    this.attackDamage = attackDamage;
    this.attackCooldownTime = attackCooldownTime;
    this.kind = kind;
  }

  public String kind() {
    return kind;
  }

  public float speed() {
    return speed;
  }

  public double attackDamage() {
    return attackDamage;
  }

  public void applyKnockback(Vec2 force) {
    this.knockback = this.knockback.add(force);
  }

  /**
   * Advances simulation for this enemy by {@code dt}:
   * 1. Decays timers and knockback.
   * 2. Computes movement direction via {@link #computeMoveDirection}.
   * 3. Applies soft separation from other enemies.
   * 4. Steps movement with wall sliding via {@link PlayerMotion}.
   * 5. Performs melee attacks against overlapping alive target actors.
   */
  public void tick(
      double dt,
      TileMap map,
      Collection<? extends Actor> targets,
      Collection<? extends Enemy> enemies) {
    if (isDead()) {
      return;
    }

    attackCooldown = Math.max(0, attackCooldown - dt);
    knockback = knockback.scale((float) Math.max(0, 1.0 - dt * GameConfig.KNOCKBACK_DECAY));

    // Compute desired move direction toward target(s).
    Vec2 desiredDir = computeMoveDirection(map, targets, dt);

    // Apply soft enemy-to-enemy separation so enemies don't stack on each other.
    if (enemies != null) {
      Vec2 separation = Vec2.ZERO;
      float myRadius = size.x * 0.5f;
      for (Enemy other : enemies) {
        if (other == this || !other.isAlive()) {
          continue;
        }
        float minDist = myRadius + other.size.x * 0.5f;
        Vec2 diff = pos.sub(other.pos());
        float dist = diff.length();
        if (dist > 1e-4f && dist < minDist) {
          float strength = (minDist - dist) / minDist;
          separation = separation.add(diff.normalized().scale(strength));
        }
      }
      if (separation.lengthSq() > 1e-4f) {
        desiredDir = desiredDir.add(separation.scale(1.2f));
      }
    }

    float dirLen = desiredDir.length();
    if (dirLen > 1e-4f) {
      desiredDir = desiredDir.scale(1.0f / dirLen);
      facing = (float) Math.atan2(desiredDir.y, desiredDir.x);
    }

    // Combine desired movement with residual knockback.
    Vec2 moveDelta = desiredDir.scale(speed).add(knockback);
    float totalSpeed = moveDelta.length();
    if (totalSpeed > 1e-4f) {
      Vec2 normMove = moveDelta.scale(1.0f / totalSpeed);
      pos = PlayerMotion.step(map, pos, size, normMove.x, normMove.y, totalSpeed, dt);
    }

    // Check melee contact attack with target actors (players).
    if (attackCooldown <= 0 && targets != null) {
      for (Actor target : targets) {
        if (!target.isDead() && bounds().intersects(target.bounds())) {
          target.damage(attackDamage);
          attackCooldown = attackCooldownTime;
          break;
        }
      }
    }
  }

  /** The AI seam: compute the desired normalized movement direction vector for this enemy. */
  protected abstract Vec2 computeMoveDirection(
      TileMap map, Collection<? extends Actor> targets, double dt);

  public EntityState toEntityState() {
    return new EntityState(id, kind, pos.x, pos.y, (float) facing, (float) health, -1);
  }
}
