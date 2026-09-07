package kittens.common.weapon;

/**
 * The prototype's weapons. All the tuning lives here so the server's fire logic and the client's
 * HUD/rendering stay in agreement; {@link #id()} is what travels on the wire.
 */
public enum Weapon {
  //       sprite     interval  dmg  pellets  spread  speed  life   name
  PISTOL  ("pistol",   0.28,    16,   1,      0.00f,  460f,  1.3,  "Pistol"),
  SHOTGUN ("shotgun",  0.72,     9,   6,      0.32f,  380f,  0.45, "Shotgun"),
  RIFLE   ("ak",       0.10,     8,   1,      0.05f,  520f,  1.1,  "Rifle"),
  BAZOOKA ("bazooka",  1.25,    60,   1,      0.00f,  300f,  2.4,  "Bazooka");

  private static final Weapon[] BY_ID = values();

  /** Resource key: {@code weapons/<sprite>.png}. */
  public final String sprite;
  /** Seconds between shots while the trigger is held. */
  public final double fireInterval;
  /** Damage per projectile. */
  public final double damage;
  /** Projectiles per shot. */
  public final int pellets;
  /** Half-angle of the random spread cone, in radians. */
  public final float spread;
  public final float projectileSpeed;
  public final double projectileLifetime;
  public final String displayName;

  Weapon(String sprite, double fireInterval, double damage, int pellets, float spread,
      float projectileSpeed, double projectileLifetime, String displayName) {
    this.sprite = sprite;
    this.fireInterval = fireInterval;
    this.damage = damage;
    this.pellets = pellets;
    this.spread = spread;
    this.projectileSpeed = projectileSpeed;
    this.projectileLifetime = projectileLifetime;
    this.displayName = displayName;
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
