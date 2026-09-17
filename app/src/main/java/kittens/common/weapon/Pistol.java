package kittens.common.weapon;

/** Starting sidearm: accurate and unremarkable. */
public class Pistol extends Weapon {
  Pistol() {
    super(0, "pistol", "Pistol",
        /* fireInterval */ 0.28, /* damage */ 16, /* pellets */ 1, /* spread */ 0.00f,
        /* projectileSpeed */ 460f, /* projectileLifetime */ 1.3,
        /* magazineSize */ 12, /* reloadTime */ 1.1);
  }
}
