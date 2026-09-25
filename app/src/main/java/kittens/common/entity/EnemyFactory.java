package kittens.common.entity;

import java.util.Objects;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;

/**
 * Factory for creating {@link Enemy} instances by {@link EntityKind} (Factory pattern).
 * Encapsulates concrete enemy instantiation, decoupling callers from specific enemy subclasses.
 */
public class EnemyFactory {

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
