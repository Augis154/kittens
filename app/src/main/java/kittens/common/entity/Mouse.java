package kittens.common.entity;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.sim.PathField;

/** Swarming enemy: fast, fragile, weaves sideways around its pursuit direction. */
public class Mouse extends Enemy {
  private double weaveTime;

  public Mouse(int id, Vec2 pos) {
    super(id, pos, Vec2.of(GameConfig.MOUSE_SIZE, GameConfig.MOUSE_SIZE),
        GameConfig.MOUSE_MAX_HEALTH, GameConfig.MOUSE_SPEED, GameConfig.MOUSE_DAMAGE,
        GameConfig.MOUSE_ATTACK_COOLDOWN, EntityKind.MOUSE);
    this.weaveTime = id * 1.618; // desynchronise the weave between mice
  }

  @Override
  protected Vec2 computeMoveDirection(TileMap map, PathField pursuit,
      Collection<? extends Actor> targets, double dt) {
    weaveTime += dt * 6.0;
    Actor closest = findClosestTarget(targets);
    if (closest == null) {
      return Vec2.ZERO;
    }
    Vec2 dir = steerToward(map, pursuit, closest.pos());
    if (dir.lengthSq() < 1e-4f) {
      return Vec2.ZERO;
    }
    Vec2 perp = Vec2.of(-dir.y, dir.x);
    return dir.add(perp.scale((float) Math.sin(weaveTime) * 0.45f)).normalized();
  }
}
