package kittens.common.weapon;

/** The shipped family: the four weapons the sprites and hotkeys 1-4 are drawn for. */
final class StandardWeaponFactory extends WeaponFactory {
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
