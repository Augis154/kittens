package kittens.common.weapon;

/**
 * The prototype's weapons. All the tuning lives here so the server's fire logic and the client's
 * HUD/rendering stay in agreement; {@link #id()} is what travels on the wire.
 */
public enum Weapon {
  //       sprite     interval dmg pellets spread  speed  life   name       boomR boomDmg recoil  mag reload
  PISTOL  ("pistol",  0.28,    16,  1,     0.00f,  460f,  1.3,  "Pistol",   0f,    0,     0f,    12, 1.1),
  SHOTGUN ("shotgun", 0.72,     9,  6,     0.32f,  380f,  0.45, "Shotgun",  0f,    0,     0f,     6, 1.7),
  RIFLE   ("ak",      0.10,     8,  1,     0.05f,  520f,  1.1,  "Rifle",    0f,    0,     0f,    30, 2.0),
  BAZOOKA ("bazooka", 1.25,    35,  1,     0.00f,  300f,  2.4,  "Bazooka", 78f,   45,   240f,     1, 1.5);

  private static final Weapon[] BY_ID = values();

  /** Resource key: {@code weapons/<sprite>.png}. */
  public final String sprite;
  /** Seconds between shots while the trigger is held. */
  public final double fireInterval;
  /** Direct-hit damage per projectile. */
  public final double damage;
  /** Projectiles per shot. */
  public final int pellets;
  /** Half-angle of the random spread cone, in radians. */
  public final float spread;
  public final float projectileSpeed;
  public final double projectileLifetime;
  public final String displayName;
  /** Blast radius in pixels when a projectile from this weapon dies; 0 = no explosion. */
  public final float explosionRadius;
  /** Peak splash damage at the blast centre, falling off to a quarter at the edge. */
  public final double explosionDamage;
  /** Self-knockback impulse (px/s) shoved onto the shooter, opposite the aim; 0 = no recoil. */
  public final float recoil;
  /** Rounds per magazine; the kitten reloads when it hits 0. Reserve ammo is unlimited. */
  public final int magazineSize;
  /** Seconds to reload a full magazine. */
  public final double reloadTime;

  Weapon(String sprite, double fireInterval, double damage, int pellets, float spread,
      float projectileSpeed, double projectileLifetime, String displayName,
      float explosionRadius, double explosionDamage, float recoil,
      int magazineSize, double reloadTime) {
    this.sprite = sprite;
    this.fireInterval = fireInterval;
    this.damage = damage;
    this.pellets = pellets;
    this.spread = spread;
    this.projectileSpeed = projectileSpeed;
    this.projectileLifetime = projectileLifetime;
    this.displayName = displayName;
    this.explosionRadius = explosionRadius;
    this.explosionDamage = explosionDamage;
    this.recoil = recoil;
    this.magazineSize = magazineSize;
    this.reloadTime = reloadTime;
  }

  public boolean explosive() {
    return explosionRadius > 0f;
  }

  public int id() {
    return ordinal();
  }

  public static Weapon byId(int id) {
    return id < 0 || id >= BY_ID.length ? PISTOL : BY_ID[id];
  }

  public static int count() {
    return BY_ID.length;
  }
}
