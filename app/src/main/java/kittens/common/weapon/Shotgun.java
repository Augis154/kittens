package kittens.common.weapon;

/** Close-range crowd clearer: wide spread, short projectile life. */
public class Shotgun extends Weapon {
  Shotgun() {
    super(1, "shotgun", "Shotgun",
        /* fireInterval */ 0.72, /* damage */ 9, /* pellets */ 6, /* spread */ 0.32f,
        /* projectileSpeed */ 380f, /* projectileLifetime */ 0.45,
        /* magazineSize */ 6, /* reloadTime */ 1.7);
  }
}
