package kittens.common.entity;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;

/**
 * Abstract computer-controlled enemy base class extending {@link Actor}.
 *
 * <p>The movement decision is encapsulated in the virtual method {@link
 * #computeMoveDirection(TileMap, Collection, double)}, which serves as the seam where specialized
 * AI behaviors and strategy patterns can be attached.
 *
 * <p>Enemies collide with walls and players (stopping/sliding against them without pushing players),
 * but pass through other enemies.
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
   * 3. Steps movement with sliding against walls and players (without pushing players).
   * 4. Performs melee attacks against overlapping alive target actors.
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
      pos = stepEnemyMovement(map, pos, size, normMove.x, normMove.y, totalSpeed, dt, targets);
    }

    // Check melee contact attack with target actors (players).
    if (attackCooldown <= 0 && targets != null) {
      // Expanded attack bounds (4px reach in all directions) so adjacent/touching enemies reliably hit
      Aabb attackReach = Aabb.fromCenter(pos, size.add(Vec2.of(8f, 8f)));
      for (Actor target : targets) {
        if (!target.isDead() && attackReach.intersects(target.bounds())) {
          target.damage(attackDamage);
          attackCooldown = attackCooldownTime;
          break;
        }
      }
    }
  }

  /**
   * Moves enemy with axis-separated sliding against map walls and players.
   * Collides only with walls and players; never pushes players.
   */
  private Vec2 stepEnemyMovement(
      TileMap map,
      Vec2 from,
      Vec2 boxSize,
      float moveX,
      float moveY,
      float moveSpeed,
      double dt,
      Collection<? extends Actor> players) {
    Vec2 dir = Vec2.of(moveX, moveY);
    float len = dir.length();
    if (len < 1e-4f || dt <= 0) {
      return from;
    }
    if (len > 1f) {
      dir = dir.scale(1f / len);
    }
    Vec2 delta = dir.scale((float) (moveSpeed * dt));
    Vec2 next = moveAxisEnemy(map, from, boxSize, delta.x, 0f, players);
    return moveAxisEnemy(map, next, boxSize, 0f, delta.y, players);
  }

  private Vec2 moveAxisEnemy(
      TileMap map,
      Vec2 from,
      Vec2 boxSize,
      float dx,
      float dy,
      Collection<? extends Actor> players) {
    Vec2 target = from.add(Vec2.of(dx, dy));
    if (!isBlocked(map, target, boxSize, players)) {
      return target;
    }
    float clear = 0f;
    float blocked = 1f;
    for (int i = 0; i < 6; i++) {
      float mid = (clear + blocked) * 0.5f;
      Vec2 candidate = from.add(Vec2.of(dx * mid, dy * mid));
      if (isBlocked(map, candidate, boxSize, players)) {
        blocked = mid;
      } else {
        clear = mid;
      }
    }
    return from.add(Vec2.of(dx * clear, dy * clear));
  }

  private boolean isBlocked(
      TileMap map,
      Vec2 candidatePos,
      Vec2 boxSize,
      Collection<? extends Actor> players) {
    // 1. Map walls
    if (map.overlapsWall(Aabb.fromCenter(candidatePos, boxSize))) {
      return true;
    }
    // 2. Players (only alive players block enemy movement; enemies cannot push players)
    if (players != null) {
      Aabb candidateBox = Aabb.fromCenter(candidatePos, boxSize);
      for (Actor p : players) {
        if (!p.isDead() && candidateBox.intersects(p.bounds())) {
          return true;
        }
      }
    }
    return false;
  }

  /** The AI seam: compute the desired normalized movement direction vector for this enemy. */
  protected abstract Vec2 computeMoveDirection(
      TileMap map, Collection<? extends Actor> targets, double dt);

  public EntityState toEntityState() {
    return new EntityState(id, kind, pos.x, pos.y, (float) facing, (float) health, -1);
  }
}
