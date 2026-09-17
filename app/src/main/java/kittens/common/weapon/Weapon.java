package kittens.common.weapon;

/**
 * A weapon's tuning. Every weapon is one of the Singleton constants below (a small registry), so
 * code may compare weapons with {@code ==}; subclass constructors are package-private to keep that
 * true. {@link #id()} is the wire value and the registry index, and adding a weapon means appending
 * with the next id — never renumbering.
 */
public abstract class Weapon {
  public static final Weapon PISTOL = new Pistol();
  public static final Weapon SHOTGUN = new Shotgun();
  public static final Weapon RIFLE = new Rifle();
  public static final Weapon BAZOOKA = new Bazooka();

  /** Indexed by {@link #id()}, which is therefore also the hotkey order. */
  private static final Weapon[] BY_ID = {PISTOL, SHOTGUN, RIFLE, BAZOOKA};

  static {
    for (int i = 0; i < BY_ID.length; i++) {
      if (BY_ID[i].id() != i) {
        throw new IllegalStateException("weapon id must match its registry index: " + BY_ID[i]);
      }
    }
  }

  private final int id;
  private final String sprite;
  private final String displayName;
  private final double fireInterval;
  private final double damage;
  private final int pellets;
  private final float spread;
  private final float projectileSpeed;
  private final double projectileLifetime;
  private final int magazineSize;
  private final double reloadTime;
  private final float explosionRadius;
  private final double explosionDamage;
  private final float recoil;

  /** Non-explosive, no-recoil weapon. */
  protected Weapon(int id, String sprite, String displayName, double fireInterval, double damage,
      int pellets, float spread, float projectileSpeed, double projectileLifetime,
      int magazineSize, double reloadTime) {
    this(id, sprite, displayName, fireInterval, damage, pellets, spread, projectileSpeed,
        projectileLifetime, magazineSize, reloadTime, 0f, 0, 0f);
  }

  protected Weapon(int id, String sprite, String displayName, double fireInterval, double damage,
      int pellets, float spread, float projectileSpeed, double projectileLifetime,
      int magazineSize, double reloadTime, float explosionRadius, double explosionDamage,
      float recoil) {
    this.id = id;
    this.sprite = sprite;
    this.displayName = displayName;
    this.fireInterval = fireInterval;
    this.damage = damage;
    this.pellets = pellets;
    this.spread = spread;
    this.projectileSpeed = projectileSpeed;
    this.projectileLifetime = projectileLifetime;
    this.magazineSize = magazineSize;
    this.reloadTime = reloadTime;
    this.explosionRadius = explosionRadius;
    this.explosionDamage = explosionDamage;
    this.recoil = recoil;
  }

  public int id() {
    return id;
  }

  /** Resource key: {@code weapons/<sprite>.png}. */
  public String sprite() {
    return sprite;
  }

  public String displayName() {
    return displayName;
  }

  /** Seconds between shots while the trigger is held. */
  public double fireInterval() {
    return fireInterval;
  }

  /** Direct-hit damage per projectile. */
  public double damage() {
    return damage;
  }

  /** Projectiles per shot. */
  public int pellets() {
    return pellets;
  }

  /** Half-angle of the spread cone, radians. */
  public float spread() {
    return spread;
  }

  public float projectileSpeed() {
    return projectileSpeed;
  }

  public double projectileLifetime() {
    return projectileLifetime;
  }

  /** Rounds per magazine; reserve ammo is unlimited. */
  public int magazineSize() {
    return magazineSize;
  }

  public double reloadTime() {
    return reloadTime;
  }

  /** Blast radius in pixels when a projectile dies; 0 = no explosion. */
  public float explosionRadius() {
    return explosionRadius;
  }

  /** Splash damage at the blast centre, falling off to a quarter at the rim. */
  public double explosionDamage() {
    return explosionDamage;
  }

  /** Self-knockback (px/s) pushed onto the shooter, opposite the aim. */
  public float recoil() {
    return recoil;
  }

  public boolean explosive() {
    return explosionRadius > 0f;
  }

  @Override
  public String toString() {
    return displayName;
  }

  /** The weapon with this wire id, or the pistol for anything out of range. */
  public static Weapon byId(int id) {
    return id < 0 || id >= BY_ID.length ? PISTOL : BY_ID[id];
  }

  public static int count() {
    return BY_ID.length;
  }
}
