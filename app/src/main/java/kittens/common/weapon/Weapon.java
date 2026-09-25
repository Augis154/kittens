package kittens.common.weapon;

/**
 * A weapon's tuning. Every weapon is one of the Singleton constants below (a small registry), so
 * code may compare weapons with {@code ==}; subclass constructors are package-private to keep that
 * true, and the registry is built once from a {@link WeaponFactory} (Abstract Factory) so the
 * arsenal in play is chosen in one place. {@link #id()} is the wire value and the registry index,
 * stamped from the factory's role order rather than declared per weapon — so adding a role means
 * appending to the factory, never renumbering.
 */
public abstract class Weapon {
  /** The family in play. Swapping arsenals is this line and nothing else. */
  private static final WeaponFactory FACTORY = new StandardWeaponFactory();

  /**
   * The family, in the factory's role order — which is therefore the id and hotkey order. Declared
   * before the constants below because static initialisers run in source order.
   */
  private static final Weapon[] ARSENAL = FACTORY.createArsenal();

  static {
    // The factory's role order defines the ids, so a family cannot number itself wrongly.
    for (int i = 0; i < ARSENAL.length; i++) {
      ARSENAL[i].id = i;
    }
  }

  /**
   * The family's products, named by their {@link WeaponFactory} role rather than by the class the
   * current family happens to supply — an alternate arsenal answers a role with its own weapon.
   */
  public static final Weapon SIDEARM = ARSENAL[0];
  public static final Weapon SCATTERGUN = ARSENAL[1];
  public static final Weapon AUTOMATIC = ARSENAL[2];
  public static final Weapon LAUNCHER = ARSENAL[3];

  /** Not final: the registry stamps it from the factory's role order before anyone can read it. */
  private int id;

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

  /** Wire value and registry index; see {@link WeaponFactory} for where the order comes from. */
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

  /** The weapon with this wire id, or the sidearm for anything out of range. */
  public static Weapon byId(int id) {
    return id < 0 || id >= ARSENAL.length ? SIDEARM : ARSENAL[id];
  }

  public static int count() {
    return ARSENAL.length;
  }
}
