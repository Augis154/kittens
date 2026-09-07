package kittens.server;

import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;

/**
 * The server's authoritative view of one player. Holds position and the latest input; movement is
 * integrated once per world tick with axis-separated wall sliding.
 */
final class ServerPlayer {
  private static final Vec2 BOX = Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE);

  private final int id;

  private Vec2 pos;
  private float aimAngle;
  private double health = 100;

  // Written by the connection's reader thread, read by the world-loop thread.
  private volatile float wishX;
  private volatile float wishY;
  private volatile float wishAim;
  private volatile long lastSeq = -1;

  ServerPlayer(int id, Vec2 spawn) {
    this.id = id;
    this.pos = spawn;
  }

  int id() {
    return id;
  }

  /** Store the newest input, ignoring packets that arrive out of order. */
  void acceptInput(InputCommand cmd) {
    if (cmd.seq() <= lastSeq) {
      return;
    }
    lastSeq = cmd.seq();
    wishX = cmd.moveX();
    wishY = cmd.moveY();
    wishAim = cmd.aimAngle();
  }

  void integrate(double dt, TileMap map) {
    aimAngle = wishAim;

    Vec2 dir = Vec2.of(wishX, wishY);
    float len = dir.length();
    if (len < 1e-4f) {
      return;
    }
    if (len > 1f) {
      dir = dir.scale(1f / len); // keep diagonal speed equal to cardinal speed
    }
    Vec2 step = dir.scale((float) (GameConfig.PLAYER_SPEED * dt));

    // Resolve one axis at a time so the player slides along a wall instead of sticking to it.
    pos = moveAxis(map, pos, step.x, 0f);
    pos = moveAxis(map, pos, 0f, step.y);
  }

  /**
   * Move by ({@code dx}, {@code dy}) if clear; if a wall is in the way, binary-search the largest
   * fraction of the step that stays clear so the player ends up flush against it.
   */
  private static Vec2 moveAxis(TileMap map, Vec2 from, float dx, float dy) {
    Vec2 target = from.add(Vec2.of(dx, dy));
    if (!map.overlapsWall(Aabb.fromCenter(target, BOX))) {
      return target;
    }
    float clear = 0f;
    float blocked = 1f;
    for (int i = 0; i < 6; i++) {
      float mid = (clear + blocked) * 0.5f;
      if (map.overlapsWall(Aabb.fromCenter(from.add(Vec2.of(dx * mid, dy * mid)), BOX))) {
        blocked = mid;
      } else {
        clear = mid;
      }
    }
    return from.add(Vec2.of(dx * clear, dy * clear));
  }

  EntityState toEntityState() {
    return new EntityState(id, "cat", pos.x, pos.y, aimAngle, (float) health, -1);
  }
}
