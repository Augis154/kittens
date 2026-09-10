package kittens.common.weapon;

/**
 * Close-range crowd clearer: a wide pellet spread that falls off fast thanks to a short projectile
 * lifetime.
 */
public class Shotgun extends Weapon {

  static final int ID = 1;

  Shotgun() {
    super(ID, "shotgun", "Shotgun",
        /* fireInterval */ 0.72, /* damage */ 9, /* pellets */ 6, /* spread */ 0.32f,
        /* projectileSpeed */ 380f, /* projectileLifetime */ 0.45,
        /* magazineSize */ 6, /* reloadTime */ 1.7);
  }
}
