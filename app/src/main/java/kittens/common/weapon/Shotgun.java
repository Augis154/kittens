package kittens.common.weapon;

/** Close-range crowd clearer: wide spread, short projectile life. */
public class Shotgun extends Weapon {
  Shotgun() {
    super("shotgun", "Shotgun",
        0.72, 9, 6, 0.32f,
        380f, 0.45,
        6, 1.7);
  }
}
