package kittens.common.weapon.standard;

import kittens.common.weapon.Weapon;
import kittens.common.weapon.WeaponFactory;

/** The shipped family: the four weapons the sprites and hotkeys 1-4 are drawn for. */
public final class StandardWeaponFactory extends WeaponFactory {
  public StandardWeaponFactory() {
    super("standard", "Standard", "Pistol, shotgun, rifle and bazooka. Hits hard, runs dry.");
  }

  @Override
  protected Weapon createSidearm() {
    return new Pistol();
  }

  @Override
  protected Weapon createScattergun() {
    return new Shotgun();
  }

  @Override
  protected Weapon createAutomatic() {
    return new Rifle();
  }

  @Override
  protected Weapon createLauncher() {
    return new Bazooka();
  }
}
