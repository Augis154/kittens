package kittens.common.weapon;

/**
 * A weapon's tuning and the behaviour derived from it. All the numbers live in the concrete
 * subclasses so the server's fire logic and the client's HUD/rendering stay in agreement;
 * {@link #id()} is what travels on the wire.
 *
 * <p>Every weapon in the game is one of the singletons in this class ({@link #PISTOL} and friends),
 * which is why code may compare weapons with {@code ==}. Subclass constructors are package-private
 * to keep that true: the registry below is the only place instances come from.
 *
 * <p>Accessors are deliberately overridable rather than public fields, so a subclass can compute a
 * value instead of storing one.
 */
public abstract class Weapon {

  public static final Weapon PISTOL = new Pistol();
  public static final Weapon SHOTGUN = new Shotgun();
  public static final Weapon RIFLE = new Rifle();
  public static final Weapon BAZOOKA = new Bazooka();

  /** Indexed by {@link #id()}, which is therefore also the weapon's hotkey order. */
  private static final Weapon[] BY_ID = {PISTOL, SHOTGUN, RIFLE, BAZOOKA};

  static {
    for (int i = 0; i < BY_ID.length; i++) {
      if (BY_ID[i].id() != i) {
        throw new IllegalStateException(
            "weapon id must match its registry index: " + BY_ID[i].displayName());
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

  /** Wire identity, and the index into the registry; stable across client and server. */
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

  /** Half-angle of the random spread cone, in radians. */
  public float spread() {
    return spread;
  }

  public float projectileSpeed() {
    return projectileSpeed;
  }

  public double projectileLifetime() {
    return projectileLifetime;
  }

  /** Rounds per magazine; the kitten reloads when it hits 0. Reserve ammo is unlimited. */
  public int magazineSize() {
    return magazineSize;
  }

  /** Seconds to reload a full magazine. */
  public double reloadTime() {
    return reloadTime;
  }

  /** Blast radius in pixels when a projectile from this weapon dies; 0 = no explosion. */
  public float explosionRadius() {
    return explosionRadius;
  }

  /** Peak splash damage at the blast centre, falling off to a quarter at the edge. */
  public double explosionDamage() {
    return explosionDamage;
  }

  /** Self-knockback impulse (px/s) shoved onto the shooter, opposite the aim; 0 = no recoil. */
  public float recoil() {
    return recoil;
  }

  public boolean explosive() {
    return explosionRadius() > 0f;
  }

  @Override
  public String toString() {
    return displayName();
  }

  /** The weapon with this wire id, falling back to the pistol for anything out of range. */
  public static Weapon byId(int id) {
    return id < 0 || id >= BY_ID.length ? PISTOL : BY_ID[id];
  }

  public static int count() {
    return BY_ID.length;
  }
}
