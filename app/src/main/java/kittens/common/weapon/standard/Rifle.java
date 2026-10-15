package kittens.common.weapon.standard;

import kittens.common.weapon.Weapon;

/** Fully automatic: low damage per round, highest sustained output. */
public class Rifle extends Weapon {
  Rifle() {
    super("standard/ak", "Rifle",
        0.10, 8, 1, 0.05f,
        520f, 1.1,
        30, 2.0);
  }
}
