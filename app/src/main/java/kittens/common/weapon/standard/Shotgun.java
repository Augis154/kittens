package kittens.common.weapon.standard;

import kittens.common.weapon.Weapon;

/** Close-range crowd clearer: wide spread, short projectile life. */
public class Shotgun extends Weapon {
  Shotgun() {
    super("standard/shotgun", "Shotgun",
        0.72, 9, 6, 0.32f,
        380f, 0.45,
        6, 24, 1.7);
  }
}
