package kittens.common.weapon.scavenger;

import kittens.common.weapon.Weapon;

/** Scavenger sidearm: slower and weaker than the pistol, reloads almost instantly, never runs dry. */
public class Slingshot extends Weapon {
  Slingshot() {
    super("scavenger/slingshot", "Slingshot",
        0.35, 12, 1, 0.02f,
        400f, 1.0,
        8, UNLIMITED, 0.8);
  }
}
