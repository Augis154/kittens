package kittens.common.weapon.scavenger;

import kittens.common.weapon.Weapon;

/** Scavenger launcher: a small blast and a gentle kick, but three shots before it reloads. */
public class FireworkLauncher extends Weapon {
  FireworkLauncher() {
    super("scavenger/fireworks", "Fireworks",
        0.90, 20, 1, 0.03f,
        360f, 1.6,
        3, 9, 1.8,
        52f, 30, 120f);
  }
}
