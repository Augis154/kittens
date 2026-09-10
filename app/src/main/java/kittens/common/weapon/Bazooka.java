package kittens.common.weapon;

/**
 * Single-shot launcher. The only explosive weapon, and the only one that kicks the shooter
 * backwards — the recoil is a movement tool as much as a drawback.
 */
public class Bazooka extends Weapon {

  static final int ID = 3;

  Bazooka() {
    super(ID, "bazooka", "Bazooka",
        /* fireInterval */ 1.25, /* damage */ 35, /* pellets */ 1, /* spread */ 0.00f,
        /* projectileSpeed */ 300f, /* projectileLifetime */ 2.4,
        /* magazineSize */ 1, /* reloadTime */ 1.5,
        /* explosionRadius */ 78f, /* explosionDamage */ 45, /* recoil */ 240f);
  }
}
