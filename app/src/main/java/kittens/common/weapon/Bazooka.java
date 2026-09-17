package kittens.common.weapon;

/** Single-shot launcher: the only explosive, and the only one that kicks the shooter back. */
public class Bazooka extends Weapon {
  Bazooka() {
    super(3, "bazooka", "Bazooka",
        /* fireInterval */ 1.25, /* damage */ 35, /* pellets */ 1, /* spread */ 0.00f,
        /* projectileSpeed */ 300f, /* projectileLifetime */ 2.4,
        /* magazineSize */ 1, /* reloadTime */ 1.5,
        /* explosionRadius */ 78f, /* explosionDamage */ 45, /* recoil */ 240f);
  }
}
