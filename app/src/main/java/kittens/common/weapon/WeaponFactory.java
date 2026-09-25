package kittens.common.weapon;

/**
 * Abstract Factory over the arsenal: one factory supplies the whole family of weapon roles a
 * kitten can carry, so swapping families is one line in {@link Weapon} rather than edits at every
 * {@code new Pistol()}. The roles, not the classes, are the products — a family is free to answer
 * {@link #createAutomatic()} with something other than a {@link Rifle}.
 *
 * <p>This ordering <em>defines</em> the wire ids: {@link Weapon}'s registry stamps each product
 * with its position here, so no weapon declares an id and a family cannot number itself wrongly.
 * Appending a role is therefore the only safe change — reordering renumbers the wire and the
 * hotkeys. Weapon constructors are package-private to keep the Singleton guarantee (code compares
 * weapons with {@code ==}), so concrete factories live in this package.
 */
public interface WeaponFactory {
  /** Role 0: the starting sidearm, always available. */
  Weapon createSidearm();

  /** Role 1: short-range multi-pellet spread. */
  Weapon createScattergun();

  /** Role 2: sustained fully-automatic fire. */
  Weapon createAutomatic();

  /** Role 3: the explosive launcher. */
  Weapon createLauncher();

  /**
   * The family in id order. {@link Weapon} calls this exactly once at class init and caches the
   * result, which is what keeps each weapon a single instance.
   */
  default Weapon[] createArsenal() {
    return new Weapon[] {createSidearm(), createScattergun(), createAutomatic(), createLauncher()};
  }
}
