package kittens.common.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;

/**
 * Factory for creating {@link Enemy} instances (Factory pattern) via prototypical archetypes (Prototype pattern).
 * Encapsulates enemy selection (via weighted spawn tables per wave) and delegates instantiation to cloned
 * prototypes stored in an archetype registry, completely decoupling callers from specific enemy subclass
 * instantiation.
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

  private final Map<EntityKind, Enemy> prototypes = new EnumMap<>(EntityKind.class);
  private final Map<String, Enemy> archetypes = new HashMap<>();

  public EnemyFactory() {
    Rat defaultRat = new Rat(0, Vec2.ZERO);
    Mouse defaultMouse = new Mouse(0, Vec2.ZERO);
    registerPrototype(EntityKind.RAT, defaultRat);
    registerPrototype(EntityKind.MOUSE, defaultMouse);
    registerArchetype("rat", defaultRat);
    registerArchetype("mouse", defaultMouse);
  }

  public EnemyFactory(Map<EntityKind, ? extends Enemy> initialPrototypes) {
    this();
    Objects.requireNonNull(initialPrototypes, "initialPrototypes must not be null");
    initialPrototypes.forEach(this::registerPrototype);
  }

  /**
   * Registers a prototypical {@link Enemy} instance for the specified {@link EntityKind}.
   * Future spawns of {@code kind} will be created by cloning this prototype.
   */
  public void registerPrototype(EntityKind kind, Enemy prototype) {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(prototype, "prototype must not be null");
    prototypes.put(kind, prototype);
  }

  /** Returns the prototypical {@link Enemy} template registered for {@code kind}, or {@code null}. */
  public Enemy prototype(EntityKind kind) {
    return prototypes.get(kind);
  }

  /**
   * Registers an archetype prototype under a specific name (e.g. "rat_armored", "mouse_swift").
   */
  public void registerArchetype(String name, Enemy prototype) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(prototype, "prototype must not be null");
    archetypes.put(name.toLowerCase(), prototype);
  }

  /** Returns the prototype registered under {@code name}, or {@code null}. */
  public Enemy archetype(String name) {
    Objects.requireNonNull(name, "name must not be null");
    return archetypes.get(name.toLowerCase());
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
   * Selects an enemy kind appropriate for {@code wave} using weighted selection and instantiates it at {@code pos}
   * by cloning the registered archetype prototype.
   *
   * @param wave the current wave number
   * @param id the unique entity identifier
   * @param pos the spawn position in world coordinates
   * @param random the random number generator used for selection
   * @return a new {@link Enemy} instance cloned from the selected prototype
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
   * Creates an enemy of the specified {@link EntityKind} by cloning its registered {@link Prototype}.
   *
   * @param kind the kind of enemy to create (must be an enemy kind)
   * @param id the unique entity identifier
   * @param pos the spawn position in world coordinates
   * @return a new {@link Enemy} instance cloned from the prototype
   * @throws IllegalArgumentException if {@code kind} has no registered prototype
   * @throws NullPointerException if {@code kind} or {@code pos} is null
   */
  public Enemy createEnemy(EntityKind kind, int id, Vec2 pos) {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(pos, "pos must not be null");
    Enemy prototype = prototypes.get(kind);
    if (prototype == null) {
      throw new IllegalArgumentException("unsupported enemy kind: " + kind);
    }
    return prototype.clone(id, pos);
  }

  /**
   * Creates an enemy by cloning the named archetype prototype.
   *
   * @param name the archetype name
   * @param id the unique entity identifier
   * @param pos the spawn position in world coordinates
   * @return a new {@link Enemy} instance cloned from the archetype prototype
   * @throws IllegalArgumentException if {@code name} has no registered prototype
   */
  public Enemy createArchetype(String name, int id, Vec2 pos) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(pos, "pos must not be null");
    Enemy prototype = archetypes.get(name.toLowerCase());
    if (prototype == null) {
      throw new IllegalArgumentException("unrecognized enemy archetype: " + name);
    }
    return prototype.clone(id, pos);
  }
}
