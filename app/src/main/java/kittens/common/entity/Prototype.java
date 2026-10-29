package kittens.common.entity;

import kittens.common.math.Vec2;

/**
 * Prototype pattern interface (GoF). Defines operations for cloning prototypical instances into
 * active runtime objects or duplicating archetypes for variation.
 *
 * @param <T> the concrete prototype type
 */
public interface Prototype<T> {
  /**
   * Clones this prototype into an active instance at {@code pos} with the assigned {@code id}.
   *
   * @param id unique runtime entity identifier
   * @param pos initial position in world coordinates
   * @return a new active instance initialized from this prototype
   */
  T clone(int id, Vec2 pos);

  /**
   * Creates a duplicate copy of this prototype archetype (e.g. to create modified variants).
   *
   * @return a distinct prototype instance with matching configuration
   */
  T copy();
}
