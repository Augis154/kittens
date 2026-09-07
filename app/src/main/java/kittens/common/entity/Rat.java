package kittens.common.entity;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;

/**
 * Standard chasing enemy: medium health, medium speed, steady pursuit of the closest kitten.
 */
public class Rat extends Enemy {
  public Rat(int id, Vec2 pos) {
    super(
        id,
        pos,
        Vec2.of(GameConfig.RAT_SIZE, GameConfig.RAT_SIZE),
        GameConfig.RAT_MAX_HEALTH,
        GameConfig.RAT_SPEED,
        GameConfig.RAT_DAMAGE,
        GameConfig.RAT_ATTACK_COOLDOWN,
        "rat");
  }

  @Override
  protected Vec2 computeMoveDirection(
      TileMap map, Collection<? extends Actor> targets, double dt) {
    Actor closest = findClosestTarget(targets);
    if (closest == null) {
      return Vec2.ZERO;
    }

    Vec2 diff = closest.pos().sub(pos);
    if (diff.lengthSq() < 1e-4f) {
      return Vec2.ZERO;
    }
    return diff.normalized();
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
