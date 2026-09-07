package kittens.common.entity;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;

/**
 * Swarming enemy: fast, low health, scurries toward the player with dynamic weaving/jitter.
 */
public class Mouse extends Enemy {
  private double weaveTime;

  public Mouse(int id, Vec2 pos) {
    super(
        id,
        pos,
        Vec2.of(GameConfig.MOUSE_SIZE, GameConfig.MOUSE_SIZE),
        GameConfig.MOUSE_MAX_HEALTH,
        GameConfig.MOUSE_SPEED,
        GameConfig.MOUSE_DAMAGE,
        GameConfig.MOUSE_ATTACK_COOLDOWN,
        "mouse");
    this.weaveTime = (id * 1.618); // Desynchronize weaving across instances
  }

  @Override
  protected Vec2 computeMoveDirection(
      TileMap map, Collection<? extends Actor> targets, double dt) {
    weaveTime += dt * 6.0;

    Actor closest = findClosestTarget(targets);
    if (closest == null) {
      return Vec2.ZERO;
    }

    Vec2 diff = closest.pos().sub(pos);
    float dist = diff.length();
    if (dist < 1e-4f) {
      return Vec2.ZERO;
    }

    Vec2 dir = diff.scale(1.0f / dist);

    // Add lateral sinusoidal weaving to give an erratic, scurrying feel
    Vec2 perp = Vec2.of(-dir.y, dir.x);
    float weaveOffset = (float) Math.sin(weaveTime) * 0.45f;
    return dir.add(perp.scale(weaveOffset)).normalized();
  }

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
}
