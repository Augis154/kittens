package kittens.common.weapon.scavenger;

import kittens.common.weapon.Weapon;
import kittens.common.weapon.WeaponFactory;

/**
 * An alternate family answering the same four roles with improvised gear: more rounds and faster
 * reloads, less punch per shot. Its art lives in {@code weapons/scavenger/}, mirroring this package, and is found
 * by {@link Weapon#sprite()} rather than by class.
 */
public final class ScavengerWeaponFactory extends WeaponFactory {
  public ScavengerWeaponFactory() {
    super("scavenger", "Scavenger", "Improvised gear. Bigger magazines, faster reloads, softer hits.");
  }

  @Override
  protected Weapon createSidearm() {
    return new Slingshot();
  }

  @Override
  protected Weapon createScattergun() {
    return new Blunderbuss();
  }

  @Override
  protected Weapon createAutomatic() {
    return new Nailgun();
  }

  @Override
  protected Weapon createLauncher() {
    return new FireworkLauncher();
  }
}
