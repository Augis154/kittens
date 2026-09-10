package kittens.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import kittens.common.GameConfig;
import kittens.common.entity.Enemy;
import kittens.common.entity.Mouse;
import kittens.common.entity.Rat;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;

/**
 * Directs enemy wave progression and spawning.
 *
 * <p>Spawns enemies at safe locations (away from active players) in controlled waves, escalating in
 * difficulty with faster and varied enemy compositions.
 */
public final class SpawnDirector {
  /**
   * How far from every living player a spawn point has to be before a wave may use it. Sized so a
   * wave never pops into existence in view: the client shows a 800x480 world-pixel viewport, whose
   * half-diagonal is about 466px. The old value of 140px predates the map being larger than one
   * screen, and on {@code sewers.txt} it failed to exclude the four spawn points in the starting
   * room, so waves appeared a few tiles from the players and downed them on arrival.
   *
   * <p>{@link #selectSpawnPosition} falls back to the farthest point when nothing clears the bar,
   * so a map smaller than this simply always spawns as far away as it can.
   */
  private static final float MIN_SPAWN_DIST = 520f;
  private static final int MAX_ACTIVE_ENEMIES = 24;

  private final AtomicInteger nextEnemyId = new AtomicInteger(GameConfig.ENEMY_ID_BASE);
  private final Random random = new Random();

  private int wave = 1;
  private int enemiesRemainingInWave = 0;
  private double spawnCooldown = 0;
  private double waveIntermissionTimer = 2.0; // Initial delay before wave 1
  private boolean waveInProgress = false;

  public SpawnDirector() {
    prepareWave(1);
  }

  private void prepareWave(int waveNum) {
    this.wave = waveNum;
    this.enemiesRemainingInWave = 4 + waveNum * 3;
    this.waveIntermissionTimer = waveNum == 1 ? 2.0 : 3.5;
    this.waveInProgress = false;
  }

  public int currentWave() {
    return wave;
  }

  public boolean isWaveInProgress() {
    return waveInProgress;
  }

  public void tick(
      double dt,
      TileMap map,
      Collection<ServerPlayer> players,
      Collection<Enemy> activeEnemies,
      List<Enemy> newEnemiesOut) {
    if (players.isEmpty()) {
      return;
    }

    boolean hasAlivePlayer = false;
    for (ServerPlayer p : players) {
      if (!p.dead()) {
        hasAlivePlayer = true;
        break;
      }
    }
    if (!hasAlivePlayer) {
      return;
    }

    if (!waveInProgress) {
      waveIntermissionTimer -= dt;
      if (waveIntermissionTimer <= 0) {
        waveInProgress = true;
        spawnCooldown = 0;
      }
      return;
    }

    // Check if wave is completed
    if (enemiesRemainingInWave <= 0 && activeEnemies.isEmpty()) {
      prepareWave(wave + 1);
      return;
    }

    // Spawn enemies during active wave
    spawnCooldown -= dt;
    if (enemiesRemainingInWave > 0
        && activeEnemies.size() < MAX_ACTIVE_ENEMIES
        && spawnCooldown <= 0) {
      Enemy spawned = spawnNextEnemy(map, players);
      if (spawned != null) {
        newEnemiesOut.add(spawned);
        enemiesRemainingInWave--;
      }
      // Dynamic spawn cadence: 0.5s to 1.3s
      spawnCooldown = Math.max(0.5, 1.2 - wave * 0.05 + (random.nextDouble() * 0.3));
    }
  }

  private Enemy spawnNextEnemy(TileMap map, Collection<ServerPlayer> players) {
    Vec2 spawnPos = selectSpawnPosition(map, players);
    if (spawnPos == null) {
      return null;
    }

    int id = nextEnemyId.getAndIncrement();

    // Enemy type composition based on wave
    double mouseRatio = Math.min(0.65, (wave - 1) * 0.2);
    if (random.nextDouble() < mouseRatio) {
      return new Mouse(id, spawnPos);
    } else {
      return new Rat(id, spawnPos);
    }
  }

  private Vec2 selectSpawnPosition(TileMap map, Collection<ServerPlayer> players) {
    List<Vec2> spawnPoints = map.spawnPoints();
    if (spawnPoints.isEmpty()) {
      return Vec2.of(map.pixelWidth() * 0.5f, map.pixelHeight() * 0.5f);
    }

    List<Vec2> candidates = new ArrayList<>();
    Vec2 bestPoint = null;
    float maxMinDistSq = -1f;

    for (Vec2 pt : spawnPoints) {
      float minDistSq = Float.MAX_VALUE;
      for (ServerPlayer p : players) {
        if (!p.dead()) {
          float distSq = pt.sub(p.pos()).lengthSq();
          if (distSq < minDistSq) {
            minDistSq = distSq;
          }
        }
      }

      if (minDistSq >= MIN_SPAWN_DIST * MIN_SPAWN_DIST) {
        candidates.add(pt);
      }
      if (minDistSq > maxMinDistSq) {
        maxMinDistSq = minDistSq;
        bestPoint = pt;
      }
    }

    if (!candidates.isEmpty()) {
      return candidates.get(random.nextInt(candidates.size()));
    }
    return bestPoint != null ? bestPoint : spawnPoints.get(random.nextInt(spawnPoints.size()));
  }
}
