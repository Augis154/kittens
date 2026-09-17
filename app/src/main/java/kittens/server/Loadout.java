package kittens.server;

import kittens.common.weapon.Weapon;

/** A player's selected weapon, per-weapon magazines, reload timer and fire cooldown. */
final class Loadout {
  private Weapon weapon = Weapon.PISTOL;
  /** Rounds left per weapon, indexed by {@link Weapon#id()}. */
  private final int[] magazine = new int[Weapon.count()];
  private double reloadTimer; // seconds left; 0 = not reloading
  private double fireCooldown;

  Loadout() {
    refill();
  }

  Weapon weapon() {
    return weapon;
  }

  int ammo() {
    return magazine[weapon.id()];
  }

  /** 0 when ready to fire, else reload progress in (0, 1] — clamped away from 0 so the HUD sees the first tick. */
  float reloadProgress() {
    return reloadTimer <= 0 ? 0f : Math.max(1e-3f, (float) (1.0 - reloadTimer / weapon.reloadTime()));
  }

  void tick(double dt) {
    fireCooldown = Math.max(0, fireCooldown - dt);
    if (reloadTimer > 0) {
      reloadTimer -= dt;
      if (reloadTimer <= 0) {
        reloadTimer = 0;
        magazine[weapon.id()] = weapon.magazineSize();
      }
    }
  }

  /** Switching cancels a reload and cannot be gamed to skip an already-shorter cooldown. */
  void select(Weapon next) {
    if (next == weapon) {
      return;
    }
    weapon = next;
    reloadTimer = 0;
    fireCooldown = Math.min(fireCooldown, next.fireInterval());
  }

  /** Manual reload; ignored while reloading or full, so spamming the key can't restart the timer. */
  void requestReload() {
    if (reloadTimer <= 0 && ammo() < weapon.magazineSize()) {
      reloadTimer = weapon.reloadTime();
    }
  }

  /** Spends a round if the weapon can fire now; an empty magazine starts a reload instead. */
  boolean tryFire() {
    if (fireCooldown > 0 || reloadTimer > 0) {
      return false;
    }
    if (ammo() == 0) {
      reloadTimer = weapon.reloadTime();
      return false;
    }
    magazine[weapon.id()]--;
    fireCooldown = weapon.fireInterval();
    if (ammo() == 0) {
      reloadTimer = weapon.reloadTime();
    }
    return true;
  }

  /** Whether an ammo crate would do anything: a short magazine, or a reload it would cut short. */
  boolean wantsAmmo() {
    if (reloadTimer > 0) {
      return true;
    }
    for (int i = 0; i < magazine.length; i++) {
      if (magazine[i] < Weapon.byId(i).magazineSize()) {
        return true;
      }
    }
    return false;
  }

  /** Fills every magazine and cancels any reload; reserve ammo is unlimited, so this buys time. */
  void restock() {
    reloadTimer = 0;
    refill();
  }

  private void refill() {
    for (int i = 0; i < magazine.length; i++) {
      magazine[i] = Weapon.byId(i).magazineSize();
    }
  }
}
