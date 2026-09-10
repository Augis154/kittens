package kittens.common.weapon;

/** Starting sidearm: accurate, cheap to fire, unremarkable. */
public class Pistol extends Weapon {

  static final int ID = 0;

  Pistol() {
    super(ID, "pistol", "Pistol",
        /* fireInterval */ 0.28, /* damage */ 16, /* pellets */ 1, /* spread */ 0.00f,
        /* projectileSpeed */ 460f, /* projectileLifetime */ 1.3,
        /* magazineSize */ 12, /* reloadTime */ 1.1);
  }
}
