package kittens.common.entity;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;
import kittens.common.sim.Motion;
import kittens.common.sim.PathField;

/**
 * Computer-controlled enemy. {@link #tick} is a Template Method: the movement decision is the
 * abstract {@link #computeMoveDirection}, which is the seam where per-enemy Strategy objects will
 * plug in. Enemies slide against walls and living players but pass through each other.
 */
public abstract class Enemy extends Actor {
  /** Radians between successive ids when fanning a stack apart; never repeats a direction. */
  private static final double GOLDEN_ANGLE = 2.399963229728653;

  protected final EntityKind kind;
  protected final float speed;
  protected final double attackDamage;
  protected final double attackCooldownTime;
  protected double attackCooldown;

  public Enemy(int id, Vec2 pos, Vec2 size, double maxHealth, float speed, double attackDamage,
      double attackCooldownTime, EntityKind kind) {
    super(id, pos, size, maxHealth);
    this.speed = speed;
    this.attackDamage = attackDamage;
    this.attackCooldownTime = attackCooldownTime;
    this.kind = kind;
  }

  public EntityKind kind() {
    return kind;
  }

  public void tick(double dt, TileMap map, PathField pursuit, Collection<? extends Actor> targets,
      Collection<? extends Enemy> enemies) {
    if (!isAlive()) {
      return;
    }
    attackCooldown = Math.max(0, attackCooldown - dt);
    decayKnockback(dt);

    Vec2 chase = computeMoveDirection(map, pursuit, targets, dt);
    if (chase.lengthSq() > 1e-8f) {
      facing = Math.atan2(chase.y, chase.x); // face the quarry, not whoever is shoving us
    }

    Vec2 desired = chase.add(separationFrom(enemies).scale(GameConfig.ENEMY_SEPARATION_WEIGHT))
        .normalized();
    Vec2 move = desired.scale(speed).add(knockback);
    float total = move.length();
    if (total > 1e-4f) {
      Vec2 dir = move.scale(1f / total);
      pos = Motion.step(pos, size, dir.x, dir.y, total, dt,
          box -> map.overlapsWall(box) || touchesLiving(box, targets));
    }

    if (attackCooldown <= 0) {
      // 4px of reach on every side so a touching enemy lands its hit reliably.
      Aabb reach = Aabb.fromCenter(pos, size.add(Vec2.of(8f, 8f)));
      for (Actor target : targets) {
        if (target.isAlive() && reach.intersects(target.bounds())) {
          target.damage(attackDamage);
          attackCooldown = attackCooldownTime;
          break;
        }
      }
    }
  }

  private static boolean touchesLiving(Aabb box, Collection<? extends Actor> actors) {
    for (Actor a : actors) {
      if (a.isAlive() && box.intersects(a.bounds())) {
        return true;
      }
    }
    return false;
  }

  /** The AI seam: the desired (normalized) movement direction for this tick. */
  protected abstract Vec2 computeMoveDirection(TileMap map, PathField pursuit,
      Collection<? extends Actor> targets, double dt);

  /**
   * A push out of the neighbours' personal space with inverse falloff, keeping its magnitude (capped)
   * so a deep pile pushes harder than a light touch. Exactly coincident enemies are fanned apart by
   * id along the golden angle, since "away" is meaningless there.
   */
  protected Vec2 separationFrom(Collection<? extends Enemy> others) {
    Vec2 push = Vec2.ZERO;
    for (Enemy other : others) {
      if (other == this || !other.isAlive()) {
        continue;
      }
      float range = (size.x + other.size.x) * 0.5f * GameConfig.ENEMY_SEPARATION_SLACK;
      Vec2 diff = pos.sub(other.pos);
      float distance = diff.length();
      if (distance >= range) {
        continue;
      }
      if (distance < range * 0.05f) {
        push = push.add(Vec2.fromAngle(id * GOLDEN_ANGLE).scale(GameConfig.ENEMY_SEPARATION_MAX));
        continue;
      }
      push = push.add(diff.scale((range / distance - 1f) / distance));
    }
    float length = push.length();
    if (length > GameConfig.ENEMY_SEPARATION_MAX) {
      push = push.scale(GameConfig.ENEMY_SEPARATION_MAX / length);
    }
    return push;
  }

  /** The nearest living target by straight-line distance, or {@code null}. */
  protected Actor findClosestTarget(Collection<? extends Actor> targets) {
    Actor closest = null;
    float bestDistSq = Float.MAX_VALUE;
    for (Actor target : targets) {
      float distSq = pos.sub(target.pos).lengthSq();
      if (target.isAlive() && distSq < bestDistSq) {
        bestDistSq = distSq;
        closest = target;
      }
    }
    return closest;
  }

  /**
   * Straight at the target when this body has a clear run; otherwise the path field's route, so
   * open-arena movement stays smooth and corners are still rounded. Falls back to the straight
   * line when the field has no route, so a cornered enemy keeps pressing.
   */
  protected Vec2 steerToward(TileMap map, PathField pursuit, Vec2 targetPos) {
    Vec2 direct = targetPos.sub(pos);
    if (direct.lengthSq() < 1e-4f) {
      return Vec2.ZERO;
    }
    if (hasClearPath(map, targetPos)) {
      return direct.normalized();
    }
    Vec2 routed = pursuit.directionAt(pos);
    return routed.lengthSq() > 1e-4f ? routed : direct.normalized();
  }

  /** Whether this body fits along the straight line to {@code targetPos}, sampled every half tile. */
  protected boolean hasClearPath(TileMap map, Vec2 targetPos) {
    Vec2 delta = targetPos.sub(pos);
    int samples = Math.max(1, (int) Math.ceil(delta.length() / (map.tileSize() * 0.5f)));
    for (int i = 1; i <= samples; i++) {
      if (map.overlapsWall(Aabb.fromCenter(pos.add(delta.scale((float) i / samples)), size))) {
        return false;
      }
    }
    return true;
  }

  public EntityState toEntityState() {
    return new EntityState(id, kind, pos.x, pos.y, (float) facing, (float) health, -1, 0f);
  }
}
