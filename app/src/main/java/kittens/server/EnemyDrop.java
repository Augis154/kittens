package kittens.server;

import java.util.Collection;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.sim.Motion;

/**
 * Something a killed enemy left behind. Short-lived, and it skitters toward the nearest kitten
 * that would actually want it once one comes close, so a drop in the middle of a fight can be
 * grabbed without breaking off to fetch it.
 */
final class EnemyDrop extends Pickup {
  private static final Vec2 SIZE = Vec2.of(GameConfig.PICKUP_SIZE, GameConfig.PICKUP_SIZE);
  private static final float MAGNET_RANGE = 3.5f * GameConfig.TILE;
  /** Faster than a kitten walks, so backing away while firing still collects it. */
  private static final float MAGNET_SPEED = 240f;

  private double life = GameConfig.ENEMY_DROP_LIFETIME;

  EnemyDrop(int id, Vec2 pos, PickupEffect effect) {
    super(id, pos, SIZE, effect);
  }

  @Override
  void tick(double dt, TileMap map, Collection<ServerPlayer> players) {
    life -= dt;
    if (life <= 0) {
      kill();
      return;
    }
    ServerPlayer nearest = null;
    float best = MAGNET_RANGE * MAGNET_RANGE;
    for (ServerPlayer p : players) {
      float d = p.pos().sub(pos).lengthSq();
      if (p.isAlive() && d < best && effect.wanted(p)) {
        best = d;
        nearest = p;
      }
    }
    if (nearest != null && best > 1f) {
      Vec2 dir = nearest.pos().sub(pos).normalized();
      // A small enemy killed flush against a wall leaves a drop that overlaps it; collision would
      // pin that one in place, so it slides free toward the kitten, who is in the open.
      pos = map.overlapsWall(bounds())
          ? pos.add(dir.scale((float) (MAGNET_SPEED * dt)))
          : Motion.step(pos, size, dir.x, dir.y, MAGNET_SPEED, dt, map::overlapsWall);
    }
  }

  @Override
  EntityState toEntityState() {
    return new EntityState(id, effect.kind(), pos.x, pos.y, 0f, (float) Math.max(0.0, life), -1,
        0f, 0);
  }
}
