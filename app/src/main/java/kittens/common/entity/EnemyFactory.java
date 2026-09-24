package kittens.common.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;

/**
 * Factory for creating {@link Enemy} instances (Factory pattern).
 * Encapsulates enemy selection (via weighted spawn tables per wave) and concrete subclass
 * instantiation, completely decoupling callers from specific enemy classes and composition rules.
 */
public class EnemyFactory {

  /** Weighted entry for probabilistic enemy selection. */
  public record SpawnWeight(EntityKind kind, double weight) {
    public SpawnWeight {
      Objects.requireNonNull(kind, "kind must not be null");
      if (weight < 0) {
        throw new IllegalArgumentException("weight must be non-negative: " + weight);
      }
    }
  }

  /**
   * Returns the spawn weight distribution for the given wave.
   * Override or extend this to adjust wave composition or introduce new enemy types.
   */
  protected List<SpawnWeight> spawnWeightsForWave(int wave) {
    List<SpawnWeight> weights = new ArrayList<>();
    double mouseRatio = Math.clamp((wave - 1) * 0.2, 0.0, 0.65);
    weights.add(new SpawnWeight(EntityKind.RAT, 1.0 - mouseRatio));
    if (mouseRatio > 0) {
      weights.add(new SpawnWeight(EntityKind.MOUSE, mouseRatio));
    }
    return weights;
  }

  /**
   * Selects an enemy kind appropriate for {@code wave} using weighted selection and instantiates it at {@code pos}.
   *
   * @param wave the current wave number
   * @param id the unique entity identifier
   * @param pos the spawn position in world coordinates
   * @param random the random number generator used for selection
   * @return a new {@link Enemy} instance
   */
  public Enemy createForWave(int wave, int id, Vec2 pos, Random random) {
    Objects.requireNonNull(pos, "pos must not be null");
    Objects.requireNonNull(random, "random must not be null");

    List<SpawnWeight> weights = spawnWeightsForWave(wave);
    double totalWeight = 0.0;
    for (SpawnWeight sw : weights) {
      totalWeight += sw.weight();
    }

    if (totalWeight <= 0) {
      return createEnemy(EntityKind.RAT, id, pos);
    }

    double roll = random.nextDouble() * totalWeight;
    double cumulative = 0.0;
    EntityKind selectedKind = EntityKind.RAT;
    for (SpawnWeight sw : weights) {
      cumulative += sw.weight();
      if (roll <= cumulative) {
        selectedKind = sw.kind();
        break;
      }
    }

    return createEnemy(selectedKind, id, pos);
  }

  /**
   * Creates an enemy of the specified {@link EntityKind}.
   *
   * @param kind the kind of enemy to create (must be an enemy kind)
   * @param id the unique entity identifier
   * @param pos the spawn position in world coordinates
   * @return a new {@link Enemy} instance
   * @throws IllegalArgumentException if {@code kind} is not an enemy kind
   * @throws NullPointerException if {@code kind} or {@code pos} is null
   */
  public Enemy createEnemy(EntityKind kind, int id, Vec2 pos) {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(pos, "pos must not be null");
    return switch (kind) {
      case RAT -> new Rat(id, pos);
      case MOUSE -> new Mouse(id, pos);
      default -> throw new IllegalArgumentException("unsupported enemy kind: " + kind);
    };
  }
}
