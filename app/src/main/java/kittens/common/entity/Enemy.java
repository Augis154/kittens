package kittens.common.entity;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.sim.PathField;

/**
 * Abstract computer-controlled enemy base class extending {@link Actor}.
 *
 * <p>The movement decision is encapsulated in the virtual method {@link
 * #computeMoveDirection(TileMap, PathField, Collection, double)}, which serves as the seam where
 * specialized AI behaviors and strategy patterns can be attached. Subclasses pick a target with
 * {@link #findClosestTarget} and turn it into a direction with {@link #steerToward}.
 *
 * <p>Enemies collide with walls and players (stopping/sliding against them without pushing players),
 * but pass through other enemies.
 */
public abstract class Enemy extends Actor {
  /** Radians between successive ids when fanning a stack apart; spreads without ever repeating. */
  private static final double GOLDEN_ANGLE = 2.399963229728653;

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
      PathField pursuit,
      Collection<? extends Actor> targets,
      Collection<? extends Enemy> enemies) {
    if (isDead()) {
      return;
    }

    attackCooldown = Math.max(0, attackCooldown - dt);
    knockback = knockback.scale((float) Math.max(0, 1.0 - dt * GameConfig.KNOCKBACK_DECAY));

    // Compute desired move direction toward target(s).
    Vec2 chase = computeMoveDirection(map, pursuit, targets, dt);

    // Face the quarry rather than the resultant, so a crowded enemy still looks at what it is
    // chasing instead of turning to face whoever is shoving it.
    if (chase.lengthSq() > 1e-8f) {
      facing = (float) Math.atan2(chase.y, chase.x);
    }

    // Blend in a push out of the neighbours' personal space, so a wave spreads into a swarm that
    // surrounds its target instead of collapsing into a single stacked point.
    Vec2 desiredDir =
        chase.add(separationFrom(enemies).scale(GameConfig.ENEMY_SEPARATION_WEIGHT));

    float dirLen = desiredDir.length();
    if (dirLen > 1e-4f) {
      desiredDir = desiredDir.scale(1.0f / dirLen);
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
      TileMap map, PathField pursuit, Collection<? extends Actor> targets, double dt);

  /**
   * A push away from the enemies crowding this one, or {@link Vec2#ZERO} when it has room. Each
   * neighbour inside the two bodies' personal space contributes a nudge that strengthens steeply the
   * deeper they overlap, and the total keeps its magnitude (capped at
   * {@link GameConfig#ENEMY_SEPARATION_MAX}) rather than being normalised — a dozen overlapping
   * neighbours have to push harder than one that is barely touching.
   *
   * <p>This only shapes movement <em>intent</em> — enemies still pass through each other, so a
   * corridor can never gridlock. Because the push falls to nothing as soon as they are apart, a
   * swarm with nothing to chase settles instead of drifting forever.
   */
  protected Vec2 separationFrom(Collection<? extends Enemy> others) {
    if (others == null) {
      return Vec2.ZERO;
    }
    Vec2 push = Vec2.ZERO;
    for (Enemy other : others) {
      if (other == this || other.isDead()) {
        continue;
      }
      float range =
          (size.x + other.size.x) * 0.5f * GameConfig.ENEMY_SEPARATION_SLACK;
      Vec2 diff = pos.sub(other.pos());
      float distance = diff.length();
      if (distance >= range) {
        continue;
      }
      if (distance < range * 0.05f) {
        // Practically on top of each other — the direction "away" is meaningless here, and in a
        // symmetric pile every push would cancel against its opposite. Fan the stack out along the
        // golden angle by id instead: deterministic, since the server is authoritative, and
        // successive ids never pick the same way.
        double angle = id * GOLDEN_ANGLE;
        push =
            push.add(
                Vec2.of((float) Math.cos(angle), (float) Math.sin(angle))
                    .scale(GameConfig.ENEMY_SEPARATION_MAX));
        continue;
      }
      // Inverse falloff rather than linear: nothing at arm's length, but rising steeply as they
      // overlap, so a pile out-pulls its own chase and the faintest asymmetry is enough to split it.
      push = push.add(diff.scale((range / distance - 1f) / distance));
    }
    // Keep the crowding magnitude — normalising it here would make eleven overlapping neighbours
    // push no harder than one that is barely touching.
    float length = push.length();
    if (length > GameConfig.ENEMY_SEPARATION_MAX) {
      push = push.scale(GameConfig.ENEMY_SEPARATION_MAX / length);
    }
    return push;
  }

  /** The nearest living target by straight-line distance, or {@code null} if there is none. */
  protected Actor findClosestTarget(Collection<? extends Actor> targets) {
    if (targets == null || targets.isEmpty()) {
      return null;
    }
    Actor closest = null;
    float bestDistSq = Float.MAX_VALUE;
    for (Actor target : targets) {
      if (target.isDead()) {
        continue;
      }
      float distSq = pos.sub(target.pos()).lengthSq();
      if (distSq < bestDistSq) {
        bestDistSq = distSq;
        closest = target;
      }
    }
    return closest;
  }

  /**
   * The direction to chase {@code targetPos}: straight at it whenever this enemy's body has a clear
   * run, and otherwise {@code pursuit}'s route around whatever is in the way. Falls back to the
   * straight line when the field offers no route, so a cornered enemy still presses forward rather
   * than freezing.
   *
   * <p>Preferring the direct line keeps movement smooth in the open — the field only takes over once
   * a wall is actually between the two, which is exactly when tile-by-tile routing looks right.
   */
  protected Vec2 steerToward(TileMap map, PathField pursuit, Vec2 targetPos) {
    Vec2 direct = targetPos.sub(pos);
    if (direct.lengthSq() < 1e-4f) {
      return Vec2.ZERO;
    }
    if (hasClearPath(map, targetPos)) {
      return direct.normalized();
    }
    Vec2 routed = pursuit == null ? Vec2.ZERO : pursuit.directionAt(pos);
    return routed.lengthSq() > 1e-4f ? routed : direct.normalized();
  }

  /**
   * Whether this enemy's body can travel straight from here to {@code targetPos} without touching a
   * wall. Sampled every half tile, which is fine enough to catch the corners an enemy would
   * otherwise grind against, and it tests the enemy's own {@link #size} — so a rat needs a wider gap
   * than a mouse to commit to a charge.
   */
  protected boolean hasClearPath(TileMap map, Vec2 targetPos) {
    Vec2 delta = targetPos.sub(pos);
    float distance = delta.length();
    if (distance < 1e-4f) {
      return true;
    }
    int samples = Math.max(1, (int) Math.ceil(distance / (map.tileSize() * 0.5f)));
    for (int i = 1; i <= samples; i++) {
      Vec2 at = pos.add(delta.scale((float) i / samples));
      if (map.overlapsWall(Aabb.fromCenter(at, size))) {
        return false;
      }
    }
    return true;
  }

  public EntityState toEntityState() {
    return new EntityState(id, kind, pos.x, pos.y, (float) facing, (float) health, -1, 0f);
  }
}
