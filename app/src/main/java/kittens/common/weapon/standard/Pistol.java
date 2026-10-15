package kittens.common.weapon.standard;

import kittens.common.weapon.Weapon;

/** Starting sidearm: accurate and unremarkable. */
public class Pistol extends Weapon {
  Pistol() {
    super("standard/pistol", "Pistol",
        0.28, 16, 1, 0.00f,
        460f, 1.3,
        12, 1.1);
  }
}
