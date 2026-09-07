package kittens.common.sim;

import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;

/**
 * The one movement model, run identically on the server (authoritative) and the client (prediction).
 * Given a position, an input direction and a time step, it returns the new position with
 * axis-separated wall sliding. Pure and deterministic so client prediction and server simulation
 * agree.
 */
public final class PlayerMotion {
  private static final Vec2 BOX = Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE);

  private PlayerMotion() {}

  public static Vec2 step(TileMap map, Vec2 pos, float moveX, float moveY, float speed, double dt) {
    return step(map, pos, BOX, moveX, moveY, speed, dt);
  }

  public static Vec2 step(
      TileMap map, Vec2 pos, Vec2 boxSize, float moveX, float moveY, float speed, double dt) {
    Vec2 dir = Vec2.of(moveX, moveY);
    float len = dir.length();
    if (len < 1e-4f || dt <= 0) {
      return pos;
    }
    if (len > 1f) {
      dir = dir.scale(1f / len); // equal speed on the diagonals
    }
    Vec2 delta = dir.scale((float) (speed * dt));
    Vec2 next = moveAxis(map, pos, boxSize, delta.x, 0f);
    return moveAxis(map, next, boxSize, 0f, delta.y);
  }

  /**
   * Move by ({@code dx}, {@code dy}) if clear; otherwise binary-search the largest fraction of the
   * step that stays clear so the actor ends up flush against the wall.
   */
  private static Vec2 moveAxis(TileMap map, Vec2 from, Vec2 boxSize, float dx, float dy) {
    Vec2 target = from.add(Vec2.of(dx, dy));
    if (!map.overlapsWall(Aabb.fromCenter(target, boxSize))) {
      return target;
    }
    float clear = 0f;
    float blocked = 1f;
    for (int i = 0; i < 6; i++) {
      float mid = (clear + blocked) * 0.5f;
      if (map.overlapsWall(Aabb.fromCenter(from.add(Vec2.of(dx * mid, dy * mid)), boxSize))) {
        blocked = mid;
      } else {
        clear = mid;
      }
    }
    return from.add(Vec2.of(dx * clear, dy * clear));
  }
}
