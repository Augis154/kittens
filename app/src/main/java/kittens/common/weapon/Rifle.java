package kittens.common.weapon;

/** Fully automatic: low damage per round, highest sustained output. */
public class Rifle extends Weapon {
  Rifle() {
    super(2, "ak", "Rifle",
        /* fireInterval */ 0.10, /* damage */ 8, /* pellets */ 1, /* spread */ 0.05f,
        /* projectileSpeed */ 520f, /* projectileLifetime */ 1.1,
        /* magazineSize */ 30, /* reloadTime */ 2.0);
  }
}
