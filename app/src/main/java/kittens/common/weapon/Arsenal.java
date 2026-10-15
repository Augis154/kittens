package kittens.common.weapon;

/**
 * What one player carries: a {@link WeaponFactory} family, which of it is drawn, and the ammo
 * state that goes with it — per-weapon magazines, the reload timer and the fire cooldown. Built
 * only by a factory, so a slot means the same role for every player of the same family.
 *
 * <p>The ammo state is server-authoritative: only {@code ServerPlayer} ticks an arsenal, and the
 * client reads its own ammo and reload off the snapshot instead of simulating them.
 */
public final class Arsenal {
  /** Slot order. Shared instances — never mutated. */
  private final Weapon[] weapons;
  /** The family's first wire id: a snapshot names a weapon by {@code base + slot}. */
  private final int base;
  /** Rounds left per weapon, indexed as {@link #weapons}. */
  private final int[] magazine;
  private int selected; // role 0, the sidearm, is what a kitten spawns holding
  private double reloadTimer; // seconds left; 0 = not reloading
  private double fireCooldown;

  Arsenal(Weapon[] weapons, int base) {
    this.weapons = weapons;
    this.base = base;
    this.magazine = new int[weapons.length];
    refill();
  }

  public int size() {
    return weapons.length;
  }

  /** The weapon currently drawn. */
  public Weapon weapon() {
    return weapons[selected];
  }

  /** What a snapshot calls the weapon currently drawn; unique across families, unlike a slot. */
  public int wireId() {
    return base + selected;
  }

  public int ammo() {
    return magazine[selected];
  }

  /** 0 when ready to fire, else reload progress in (0, 1] — clamped away from 0 so the HUD sees the first tick. */
  public float reloadProgress() {
    return reloadTimer <= 0 ? 0f : Math.max(1e-3f, (float) (1.0 - reloadTimer / weapon().reloadTime()));
  }

  public void tick(double dt) {
    fireCooldown = Math.max(0, fireCooldown - dt);
    if (reloadTimer > 0) {
      reloadTimer -= dt;
      if (reloadTimer <= 0) {
        reloadTimer = 0;
        magazine[selected] = weapon().magazineSize();
      }
    }
  }

  /** Switching cancels a reload and cannot be gamed to skip an already-shorter cooldown. */
  public void select(int slot) {
    if (slot < 0 || slot >= weapons.length || slot == selected) {
      return;
    }
    selected = slot;
    reloadTimer = 0;
    fireCooldown = Math.min(fireCooldown, weapon().fireInterval());
  }

  /** Manual reload; ignored while reloading or full, so spamming the key can't restart the timer. */
  public void requestReload() {
    if (reloadTimer <= 0 && ammo() < weapon().magazineSize()) {
      reloadTimer = weapon().reloadTime();
    }
  }

  /** Spends a round if the weapon can fire now; an empty magazine starts a reload instead. */
  public boolean tryFire() {
    if (fireCooldown > 0 || reloadTimer > 0) {
      return false;
    }
    if (ammo() == 0) {
      reloadTimer = weapon().reloadTime();
      return false;
    }
    magazine[selected]--;
    fireCooldown = weapon().fireInterval();
    if (ammo() == 0) {
      reloadTimer = weapon().reloadTime();
    }
    return true;
  }

  /** Whether an ammo crate would do anything: a short magazine, or a reload it would cut short. */
  public boolean wantsAmmo() {
    if (reloadTimer > 0) {
      return true;
    }
    for (int i = 0; i < magazine.length; i++) {
      if (magazine[i] < weapons[i].magazineSize()) {
        return true;
      }
    }
    return false;
  }

  /** Fills every magazine and cancels any reload; reserve ammo is unlimited, so this buys time. */
  public void restock() {
    reloadTimer = 0;
    refill();
  }

  private void refill() {
    for (int i = 0; i < magazine.length; i++) {
      magazine[i] = weapons[i].magazineSize();
    }
  }
}
