package kittens.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import kittens.common.GameConfig;
import kittens.common.entity.Enemy;
import kittens.common.entity.Mouse;
import kittens.common.entity.Rat;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;

/**
 * Wave progression and enemy spawning (a simple Factory for enemies). Cadence, caps and spacing are
 * private constants rather than {@code GameConfig} because no client has to agree on them.
 */
final class SpawnDirector {
  /**
   * Spawn points closer than this to any living player are skipped, so a wave never pops into view:
   * the viewport's half-diagonal is ~466 px. Falls back to the farthest point on a smaller map.
   */
  private static final float MIN_SPAWN_DIST = 520f;
  private static final int MAX_ACTIVE_ENEMIES = 24;

  private final Random random = new Random();
  private int nextEnemyId = GameConfig.ENEMY_ID_BASE;

  private int wave;
  private int remainingInWave;
  private double spawnCooldown;
  private double intermissionTimer;
  private boolean waveInProgress;

  SpawnDirector() {
    prepareWave(1);
  }

  private void prepareWave(int number) {
    wave = number;
    remainingInWave = 4 + number * 3;
    intermissionTimer = number == 1 ? 2.0 : 3.5;
    waveInProgress = false;
  }

  /** Advances the wave clock and returns at most one newly spawned enemy, or {@code null}. */
  Enemy tick(double dt, TileMap map, Collection<ServerPlayer> players, List<Enemy> active) {
    if (players.stream().noneMatch(ServerPlayer::isAlive)) {
      return null;
    }
    if (!waveInProgress) {
      intermissionTimer -= dt;
      if (intermissionTimer <= 0) {
        waveInProgress = true;
        spawnCooldown = 0;
      }
      return null;
    }
    if (remainingInWave <= 0 && active.isEmpty()) {
      prepareWave(wave + 1);
      return null;
    }
    spawnCooldown -= dt;
    if (remainingInWave <= 0 || active.size() >= MAX_ACTIVE_ENEMIES || spawnCooldown > 0) {
      return null;
    }
    spawnCooldown = Math.max(0.5, 1.2 - wave * 0.05 + random.nextDouble() * 0.3);
    remainingInWave--;

    Vec2 at = selectSpawnPosition(map, players);
    double mouseRatio = Math.min(0.65, (wave - 1) * 0.2);
    return random.nextDouble() < mouseRatio ? new Mouse(nextEnemyId++, at) : new Rat(nextEnemyId++, at);
  }

  /** A random spawn point far from every living player, else the farthest one there is. */
  private Vec2 selectSpawnPosition(TileMap map, Collection<ServerPlayer> players) {
    List<Vec2> spawnPoints = map.spawnPoints();
    if (spawnPoints.isEmpty()) {
      return Vec2.of(map.pixelWidth() * 0.5f, map.pixelHeight() * 0.5f);
    }
    List<Vec2> candidates = new ArrayList<>();
    Vec2 farthest = null;
    float farthestDistSq = -1f;
    for (Vec2 pt : spawnPoints) {
      float minDistSq = Float.MAX_VALUE;
      for (ServerPlayer p : players) {
        if (p.isAlive()) {
          minDistSq = Math.min(minDistSq, pt.sub(p.pos()).lengthSq());
        }
      }
      if (minDistSq >= MIN_SPAWN_DIST * MIN_SPAWN_DIST) {
        candidates.add(pt);
      }
      if (minDistSq > farthestDistSq) {
        farthestDistSq = minDistSq;
        farthest = pt;
      }
    }
    return candidates.isEmpty() ? farthest : candidates.get(random.nextInt(candidates.size()));
  }
}
