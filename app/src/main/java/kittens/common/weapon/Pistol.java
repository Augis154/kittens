package kittens.common.weapon;

/** Starting sidearm: accurate and unremarkable. */
public class Pistol extends Weapon {
  Pistol() {
    super("pistol", "Pistol",
        0.28, 16, 1, 0.00f,
        460f, 1.3,
        12, 1.1);
  }
}
