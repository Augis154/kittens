package kittens.common.weapon;

/** Fully automatic: low damage per round, but the highest sustained output in the game. */
public class Rifle extends Weapon {

  static final int ID = 2;

  Rifle() {
    super(ID, "ak", "Rifle",
        /* fireInterval */ 0.10, /* damage */ 8, /* pellets */ 1, /* spread */ 0.05f,
        /* projectileSpeed */ 520f, /* projectileLifetime */ 1.1,
        /* magazineSize */ 30, /* reloadTime */ 2.0);
  }
}
