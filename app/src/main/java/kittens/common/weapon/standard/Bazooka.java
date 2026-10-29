package kittens.common.weapon.standard;

import kittens.common.weapon.Weapon;

/** Single-shot launcher: the only explosive, and the only one that kicks the shooter back. */
public class Bazooka extends Weapon {
  Bazooka() {
    super("standard/bazooka", "Bazooka",
        1.25, 35, 1, 0.00f,
        300f, 2.4,
        1, 5, 1.5,
        78f, 45, 240f);
  }
}
