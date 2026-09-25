package kittens.common.weapon;

/**
 * The shipped arsenal: the four weapons the sprites and hotkeys 1-4 are drawn for. Package-private
 * so {@link Weapon}'s registry stays the only arsenal anyone can build, and weapons stay Singletons.
 */
final class StandardWeaponFactory implements WeaponFactory {
  @Override
  public Weapon createSidearm() {
    return new Pistol();
  }

  @Override
  public Weapon createScattergun() {
    return new Shotgun();
  }

  @Override
  public Weapon createAutomatic() {
    return new Rifle();
  }

  @Override
  public Weapon createLauncher() {
    return new Bazooka();
  }
}
