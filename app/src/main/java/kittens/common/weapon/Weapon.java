package kittens.common.weapon;

/**
 * A weapon's tuning, and nothing else: immutable, id-less and unaware of the arsenal it ends up
 * in. Subclasses ({@link Pistol}, {@link Shotgun}, {@link Rifle}, {@link Bazooka}) exist only to
 * pass their numbers to this constructor.
 *
 * <p>{@link WeaponFactory} builds one instance per role and every {@link Arsenal} shares it, so
 * weapons stay Singletons and code may compare them with {@code ==}. Wire ids and per-player ammo
 * belong to {@code Arsenal}, not here.
 */
public abstract class Weapon {
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
  protected Weapon(String sprite, String displayName, double fireInterval, double damage,
      int pellets, float spread, float projectileSpeed, double projectileLifetime,
      int magazineSize, double reloadTime) {
    this(sprite, displayName, fireInterval, damage, pellets, spread, projectileSpeed,
        projectileLifetime, magazineSize, reloadTime, 0f, 0, 0f);
  }

  protected Weapon(String sprite, String displayName, double fireInterval, double damage,
      int pellets, float spread, float projectileSpeed, double projectileLifetime,
      int magazineSize, double reloadTime, float explosionRadius, double explosionDamage,
      float recoil) {
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
}
