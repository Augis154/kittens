package kittens.common.weapon;

/** Single-shot launcher: the only explosive, and the only one that kicks the shooter back. */
public class Bazooka extends Weapon {
  Bazooka() {
    super(3, "bazooka", "Bazooka",
        1.25, 35, 1, 0.00f,
        300f, 2.4,
        1, 1.5,
        78f, 45, 240f);
  }
}
