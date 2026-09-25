package kittens.common.weapon;

/**
 * Abstract Factory over the arsenal: one factory supplies the whole family of weapon roles a
 * kitten carries, so swapping families is one line here rather than edits at every
 * {@code new Pistol()}. The roles, not the classes, are the products — a family is free to answer
 * {@link #createAutomatic()} with something other than a {@link Rifle}.
 *
 * <p>The role order <em>defines</em> the wire ids: {@link #weapon(int)} decodes an id, and an
 * {@link Arsenal} stores its magazines in the same order. Appending a role is therefore the only
 * safe change — reordering renumbers the wire, the magazines and the hotkeys at once.
 *
 * <p>Products are built once and shared by every arsenal, which is what keeps weapons Singletons
 * (code compares them with {@code ==}). Weapon constructors are package-private, so concrete
 * families live in this package.
 */
public abstract class WeaponFactory {
  /** The family in play. Swapping arsenals is this line and nothing else. */
  private static final WeaponFactory ACTIVE = new StandardWeaponFactory();

  /** A fresh arsenal for one player: the active family's weapons, full magazines, sidearm drawn. */
  public static Arsenal newArsenal() {
    return ACTIVE.createArsenal();
  }

  /** The active family's weapon for this wire id, or its sidearm for anything out of range. */
  public static Weapon weapon(int id) {
    Weapon[] family = ACTIVE.family();
    return id < 0 || id >= family.length ? family[0] : family[id];
  }

  /** How many roles the active family has; also the number of hotkeys and HUD slots. */
  public static int count() {
    return ACTIVE.family().length;
  }

  private Weapon[] family;

  /** Role 0: the starting sidearm, always available. */
  protected abstract Weapon createSidearm();

  /** Role 1: short-range multi-pellet spread. */
  protected abstract Weapon createScattergun();

  /** Role 2: sustained fully-automatic fire. */
  protected abstract Weapon createAutomatic();

  /** Role 3: the explosive launcher. */
  protected abstract Weapon createLauncher();

  /** A new arsenal over this family's shared products. */
  public final Arsenal createArsenal() {
    return new Arsenal(family());
  }

  /** The products in role order, built on first use so every arsenal shares one per role. */
  private Weapon[] family() {
    if (family == null) {
      family = new Weapon[] {createSidearm(), createScattergun(), createAutomatic(),
          createLauncher()};
    }
    return family;
  }
}
