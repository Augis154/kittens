package kittens.common.sim;

import java.util.function.DoublePredicate;
import java.util.function.Predicate;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;

/**
 * The one movement model: axis-separated sliding against whatever {@code blocked} refuses. The
 * player overload is run identically by the server and by the client's predictor, so it must stay
 * pure and deterministic — any divergence shows up as rubber-banding.
 */
public final class Motion {
  private static final Vec2 PLAYER_BOX = Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE);

  private Motion() {}

  /** Player movement: a {@link GameConfig#PLAYER_SIZE} box blocked by walls only. */
  public static Vec2 step(TileMap map, Vec2 pos, float moveX, float moveY, float speed, double dt) {
    return step(pos, PLAYER_BOX, moveX, moveY, speed, dt, map::overlapsWall);
  }

  public static Vec2 step(Vec2 pos, Vec2 box, float moveX, float moveY, float speed, double dt,
      Predicate<Aabb> blocked) {
    Vec2 dir = Vec2.of(moveX, moveY);
    float len = dir.length();
    if (len < 1e-4f || dt <= 0) {
      return pos;
    }
    if (len > 1f) {
      dir = dir.scale(1f / len); // equal speed on the diagonals
    }
    Vec2 delta = dir.scale((float) (speed * dt));
    Vec2 next = moveAxis(pos, box, Vec2.of(delta.x, 0f), blocked);
    return moveAxis(next, box, Vec2.of(0f, delta.y), blocked);
  }

  /** Full step if clear, else the largest clear fraction so the body ends flush against the obstacle. */
  private static Vec2 moveAxis(Vec2 from, Vec2 box, Vec2 delta, Predicate<Aabb> blocked) {
    DoublePredicate blockedAt = t -> blocked.test(Aabb.fromCenter(from.add(delta.scale((float) t)), box));
    if (!blockedAt.test(1.0)) {
      return from.add(delta);
    }
    return from.add(delta.scale(largestClear(0f, 1f, blockedAt)));
  }

  /** Bisects between a clear and a blocked fraction six times and returns the clear end. */
  public static float largestClear(float clear, float blocked, DoublePredicate blockedAt) {
    for (int i = 0; i < 6; i++) {
      float mid = (clear + blocked) * 0.5f;
      if (blockedAt.test(mid)) {
        blocked = mid;
      } else {
        clear = mid;
      }
    }
    return clear;
  }
}
