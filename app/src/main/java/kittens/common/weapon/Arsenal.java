package kittens.common.weapon;

/**
 * What one player carries: a {@link WeaponFactory} family, which of it is drawn, and the ammo
 * state that goes with it — per-weapon magazines and reserves, the reload timer and the fire
 * cooldown. Built only by a factory, so a slot means the same role for every player of the same
 * family. A reload moves rounds from the reserve into the magazine; a weapon whose reserve is
 * {@link Weapon#UNLIMITED} (the sidearm) never runs dry, so a kitten is never left unarmed.
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
  /** Spare rounds per weapon; meaningless for an unlimited one. */
  private final int[] reserve;
  private int selected; // role 0, the sidearm, is what a kitten spawns holding
  private double reloadTimer; // seconds left; 0 = not reloading
  private double fireCooldown;

  Arsenal(Weapon[] weapons, int base) {
    this.weapons = weapons;
    this.base = base;
    this.magazine = new int[weapons.length];
    this.reserve = new int[weapons.length];
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

  public int ammo(int slot) {
    return magazine[slot];
  }

  /** Spare rounds for {@code slot}, or {@link Weapon#UNLIMITED}. */
  public int reserve(int slot) {
    return weapons[slot].unlimited() ? Weapon.UNLIMITED : reserve[slot];
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
        int take = Math.min(weapon().magazineSize() - magazine[selected], spare(selected));
        magazine[selected] += take;
        if (!weapon().unlimited()) {
          reserve[selected] -= take;
        }
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

  /**
   * Manual reload; ignored while reloading, when full, or with nothing in reserve, so spamming the
   * key can't restart the timer.
   */
  public void requestReload() {
    startReload();
  }

  /** Spends a round if the weapon can fire now; an empty magazine starts a reload instead, if it can. */
  public boolean tryFire() {
    if (fireCooldown > 0 || reloadTimer > 0) {
      return false;
    }
    if (ammo() == 0) {
      startReload();
      return false;
    }
    magazine[selected]--;
    fireCooldown = weapon().fireInterval();
    if (ammo() == 0) {
      startReload();
    }
    return true;
  }

  /** Out of rounds in both the magazine and the reserve; only a limited weapon can be. */
  public boolean empty(int slot) {
    return magazine[slot] == 0 && spare(slot) == 0;
  }

  /** Whether an ammo pickup would do anything: some limited weapon has room in its reserve. */
  public boolean wantsAmmo() {
    for (int i = 0; i < weapons.length; i++) {
      if (!weapons[i].unlimited() && reserve[i] < weapons[i].reserveCapacity()) {
        return true;
      }
    }
    return false;
  }

  /** Tops every limited weapon's reserve up by {@code share} of its capacity, capped at full. */
  public void addReserve(double share) {
    for (int i = 0; i < weapons.length; i++) {
      int capacity = weapons[i].reserveCapacity();
      if (!weapons[i].unlimited()) {
        reserve[i] = Math.min(capacity, reserve[i] + (int) Math.ceil(capacity * share));
      }
    }
    if (ammo() == 0) {
      startReload(); // a kitten clicking on an empty weapon shouldn't have to press R as well
    }
  }

  /** Back to a full load: every magazine and reserve, and no reload in flight. For a respawn. */
  public void restock() {
    reloadTimer = 0;
    refill();
  }

  private void startReload() {
    if (reloadTimer <= 0 && ammo() < weapon().magazineSize() && spare(selected) > 0) {
      reloadTimer = weapon().reloadTime();
    }
  }

  /** Rounds a reload of {@code slot} could draw on. */
  private int spare(int slot) {
    return weapons[slot].unlimited() ? Integer.MAX_VALUE : reserve[slot];
  }

  private void refill() {
    for (int i = 0; i < magazine.length; i++) {
      magazine[i] = weapons[i].magazineSize();
      reserve[i] = Math.max(0, weapons[i].reserveCapacity());
    }
  }
}
