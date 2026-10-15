package kittens.common.weapon.scavenger;

import kittens.common.weapon.Weapon;

/** Scavenger automatic: a faster, deeper, weaker stream than the rifle. */
public class Nailgun extends Weapon {
  Nailgun() {
    super("scavenger/nailgun", "Nailgun",
        0.07, 5, 1, 0.08f,
        560f, 0.9,
        40, 2.4);
  }
}
