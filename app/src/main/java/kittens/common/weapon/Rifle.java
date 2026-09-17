package kittens.common.weapon;

/** Fully automatic: low damage per round, highest sustained output. */
public class Rifle extends Weapon {
  Rifle() {
    super(2, "ak", "Rifle",
        0.10, 8, 1, 0.05f,
        520f, 1.1,
        30, 2.0);
  }
}
