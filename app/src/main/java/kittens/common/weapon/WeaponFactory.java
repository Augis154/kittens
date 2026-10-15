package kittens.common.weapon;

import java.util.List;
import kittens.common.weapon.scavenger.ScavengerWeaponFactory;
import kittens.common.weapon.standard.StandardWeaponFactory;

/**
 * Abstract Factory over the arsenal: one factory supplies the whole family of weapon roles a
 * kitten carries, so a player's choice of family is one object rather than edits at every
 * {@code new Pistol()}. The roles, not the classes, are the products — a family is free to answer
 * {@link #createAutomatic()} with something other than a rifle.
 *
 * <p>Two id spaces, deliberately kept apart. A <em>slot</em> is a position within one family: the
 * hotkey, the HUD order, the magazine index, and what a client asks for in {@code InputCommand}.
 * A <em>wire id</em> is unique across every family ({@code base + slot}) and is what snapshots
 * carry, because a client must decode weapons belonging to players who chose a different family
 * than its own. Appending a role to a family, or a family to {@link #families()}, is safe;
 * reordering renumbers the wire.
 *
 * <p>Products are built once and shared by every arsenal, which is what keeps weapons Singletons
 * (code compares them with {@code ==}). Each family lives in its own subpackage and keeps its
 * weapon constructors package-private, so a weapon can only be built by the factory beside it.
 */
public abstract class WeaponFactory {
  /** Every family a player can pick, in menu order; the first is also the fallback. */
  private static final List<WeaponFactory> FAMILIES =
      List.of(new StandardWeaponFactory(), new ScavengerWeaponFactory());

  static {
    int base = 0;
    for (WeaponFactory family : FAMILIES) {
      family.base = base;
      base += family.size();
    }
  }

  public static List<WeaponFactory> families() {
    return FAMILIES;
  }

  /** The family with this {@link #name()}, or the first family for anything unrecognised. */
  public static WeaponFactory named(String name) {
    for (WeaponFactory family : FAMILIES) {
      if (family.name.equalsIgnoreCase(name)) {
        return family;
      }
    }
    return FAMILIES.get(0);
  }

  /** The weapon behind a snapshot's wire id, or the first family's sidearm for anything unknown. */
  public static Weapon decode(int wireId) {
    for (WeaponFactory family : FAMILIES) {
      if (wireId >= family.base && wireId < family.base + family.size()) {
        return family.weaponAt(wireId - family.base);
      }
    }
    return FAMILIES.get(0).weaponAt(0);
  }

  /** The slot (role) a wire id occupies within its family, or 0 for anything unknown. */
  public static int slotOf(int wireId) {
    for (WeaponFactory family : FAMILIES) {
      if (wireId >= family.base && wireId < family.base + family.size()) {
        return wireId - family.base;
      }
    }
    return 0;
  }

  private final String name;
  private final String title;
  private final String blurb;
  private Weapon[] weapons;
  /** First wire id of this family; set once, in this class's static initialiser. */
  private int base;

  /** {@code name} is the key sent in {@code Join}; {@code title} and {@code blurb} are for menus. */
  protected WeaponFactory(String name, String title, String blurb) {
    this.name = name;
    this.title = title;
    this.blurb = blurb;
  }

  /** Stable lower-case key sent in {@code Join}; also the name of this family's sprite folder. */
  public final String name() {
    return name;
  }

  public final String title() {
    return title;
  }

  public final String blurb() {
    return blurb;
  }

  /** Role 0: the starting sidearm, always available. */
  protected abstract Weapon createSidearm();

  /** Role 1: short-range multi-pellet spread. */
  protected abstract Weapon createScattergun();

  /** Role 2: sustained fully-automatic fire. */
  protected abstract Weapon createAutomatic();

  /** Role 3: the explosive launcher. */
  protected abstract Weapon createLauncher();

  /** How many slots this family has; also the number of hotkeys and HUD slots it needs. */
  public final int size() {
    return weapons().length;
  }

  /** The weapon in this slot, or the sidearm for anything out of range. */
  public final Weapon weaponAt(int slot) {
    Weapon[] all = weapons();
    return slot < 0 || slot >= all.length ? all[0] : all[slot];
  }

  /** A fresh arsenal for one player: this family's weapons, full magazines, sidearm drawn. */
  public final Arsenal createArsenal() {
    return new Arsenal(weapons(), base);
  }

  /** The products in slot order, built on first use so every arsenal shares one per role. */
  private Weapon[] weapons() {
    if (weapons == null) {
      weapons = new Weapon[] {createSidearm(), createScattergun(), createAutomatic(),
          createLauncher()};
    }
    return weapons;
  }
}
