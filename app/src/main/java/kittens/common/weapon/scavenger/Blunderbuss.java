package kittens.common.weapon.scavenger;

import kittens.common.weapon.Weapon;

/** Scavenger scattergun: a huge cone of weak pellets from a two-round magazine. */
public class Blunderbuss extends Weapon {
  Blunderbuss() {
    super("scavenger/blunderbuss", "Blunderbuss",
        1.00, 7, 9, 0.45f,
        340f, 0.40,
        2, 12, 1.6);
  }
}
