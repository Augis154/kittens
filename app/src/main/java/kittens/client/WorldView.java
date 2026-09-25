package kittens.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import kittens.common.GameConfig;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;
import kittens.common.weapon.Weapon;
import kittens.common.weapon.WeaponFactory;

/**
 * Cosmetic smoothing of everything the local player does <em>not</em> control: remote actors are
 * eased toward their snapshot positions, bullets are dead-reckoned between snapshots, and health
 * drops and vanishing pickups become effects. Nothing here feeds prediction, so it can be retuned
 * freely without desync risk.
 */
final class WorldView {
  private static final float REMOTE_SMOOTHING = 0.30f;
  private static final float BULLET_SMOOTHING = 0.25f;
  private static final float BULLET_SNAP = 48f;
  private static final float HIT_FLASH_SECONDS = 0.14f;
  /**
   * A pickup that vanished with more life than this left was collected, not expired — an expired
   * one is last seen with under a tick on the clock.
   */
  private static final float PICKUP_TAKEN_LIFE = 0.3f;

  /** A dead-reckoned projectile. */
  record Bullet(Vec2 pos, float angle, Weapon weapon) {
    Bullet moved(double dt) {
      return new Bullet(pos.add(Vec2.fromAngle(angle).scale((float) (weapon.projectileSpeed() * dt))),
          angle, weapon);
    }
  }

  /** A short-lived world-space effect. Client-only: never sent, never simulated. */
  static final class Fx {
    enum Kind {
      MUZZLE,
      HIT,
      DAMAGE,
      PICKUP
    }

    final Kind kind;
    final float angle;
    /** Damage dealt for {@link Kind#DAMAGE}. */
    final int amount;
    /** An explosive muzzle flash, or a medkit rather than an ammo crate. */
    final boolean accent;
    private final float maxLife;
    Vec2 pos;
    float life;

    private Fx(Kind kind, Vec2 pos, float angle, int amount, boolean accent, float life) {
      this.kind = kind;
      this.pos = pos;
      this.angle = angle;
      this.amount = amount;
      this.accent = accent;
      this.maxLife = life;
      this.life = life;
    }

    /** 1 when just started, easing to 0 as it expires. */
    float progress() {
      return Math.clamp(life / maxLife, 0f, 1f);
    }
  }

  private record PickupSeen(Vec2 pos, float life, boolean health) {}

  private final Map<Integer, Vec2> smoothed = new HashMap<>();
  private final Map<Integer, Bullet> bullets = new HashMap<>();
  private final Map<Integer, PickupSeen> pickupSeen = new HashMap<>();
  private final Map<Integer, Float> enemyHp = new HashMap<>();
  private final Map<Integer, Float> enemyFlash = new HashMap<>();
  private final List<Fx> effects = new ArrayList<>();

  void update(double dt, Map<Integer, EntityState> entities, int myPlayerId) {
    smoothRemotes(entities, myPlayerId);
    updateBullets(dt, entities);
    trackEnemyDamage(entities);
    trackPickups(entities);
    advanceEffects(dt);
  }

  /** Smoothed position of an actor, falling back to its snapshot position. */
  Vec2 positionOf(EntityState e) {
    Vec2 p = smoothed.get(e.id());
    return p != null ? p : Vec2.of(e.x(), e.y());
  }

  Collection<Bullet> bullets() {
    return bullets.values();
  }

  /** How lit an enemy is from a recent hit: 1 as it lands, easing to 0. */
  float hitFlash(int id) {
    return Math.clamp(enemyFlash.getOrDefault(id, 0f) / HIT_FLASH_SECONDS, 0f, 1f);
  }

  List<Fx> effects() {
    return effects;
  }

  private void smoothRemotes(Map<Integer, EntityState> live, int myPlayerId) {
    smoothed.keySet().removeIf(id -> id == myPlayerId || !live.containsKey(id));
    for (EntityState e : live.values()) {
      if (e.id() == myPlayerId || !e.kind().isActor()) {
        continue;
      }
      Vec2 target = Vec2.of(e.x(), e.y());
      Vec2 current = smoothed.get(e.id());
      smoothed.put(e.id(),
          current == null ? target : current.add(target.sub(current).scale(REMOTE_SMOOTHING)));
    }
  }

  private void updateBullets(double dt, Map<Integer, EntityState> live) {
    bullets.keySet().removeIf(id -> !live.containsKey(id));
    for (EntityState e : live.values()) {
      if (e.kind() != EntityKind.BULLET) {
        continue;
      }
      Weapon weapon = WeaponFactory.weapon(e.weaponId());
      Vec2 authority = Vec2.of(e.x(), e.y());
      Bullet b = bullets.get(e.id());
      if (b == null) {
        bullets.put(e.id(), new Bullet(authority, e.angle(), weapon));
        // First sighting: flash where it left the barrel a tick ago. Shotgun pellets share a
        // muzzle, so the overlapping flashes read as one bigger blast.
        float back = (float) (weapon.projectileSpeed() * GameConfig.TICK_DT);
        effects.add(new Fx(Fx.Kind.MUZZLE, authority.sub(Vec2.fromAngle(e.angle()).scale(back)),
            e.angle(), 0, weapon.explosive(), 0.09f));
      } else {
        Vec2 pos = authority.distance(b.pos()) > BULLET_SNAP
            ? authority
            : b.pos().add(authority.sub(b.pos()).scale(BULLET_SMOOTHING));
        bullets.put(e.id(), new Bullet(pos, e.angle(), weapon));
      }
    }
    if (dt > 0) {
      bullets.replaceAll((id, b) -> b.moved(dt));
    }
  }

  /** Health drops between snapshots become a flash and a floating number, each hit exactly once. */
  private void trackEnemyDamage(Map<Integer, EntityState> live) {
    enemyHp.keySet().removeIf(id -> !live.containsKey(id));
    enemyFlash.keySet().removeIf(id -> !live.containsKey(id));
    for (EntityState e : live.values()) {
      if (!e.kind().isEnemy()) {
        continue;
      }
      Float previous = enemyHp.put(e.id(), e.hp());
      if (previous == null || e.hp() >= previous) {
        continue;
      }
      Vec2 at = positionOf(e);
      enemyFlash.put(e.id(), HIT_FLASH_SECONDS);
      effects.add(new Fx(Fx.Kind.HIT, at, 0f, 0, false, 0.22f));
      effects.add(new Fx(Fx.Kind.DAMAGE, at.sub(Vec2.of(0f, GameConfig.TILE * 0.4f)), 0f,
          Math.max(1, Math.round(previous - e.hp())), false, 0.7f));
    }
  }

  /** A pickup vanishing with life to spare was walked into by somebody: pop a ring there. */
  private void trackPickups(Map<Integer, EntityState> live) {
    Iterator<Map.Entry<Integer, PickupSeen>> it = pickupSeen.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Integer, PickupSeen> entry = it.next();
      if (live.containsKey(entry.getKey())) {
        continue;
      }
      PickupSeen last = entry.getValue();
      if (last.life() > PICKUP_TAKEN_LIFE) {
        effects.add(new Fx(Fx.Kind.PICKUP, last.pos(), 0f, 0, last.health(), 0.34f));
      }
      it.remove();
    }
    for (EntityState e : live.values()) {
      if (e.kind().isPickup()) {
        pickupSeen.put(e.id(),
            new PickupSeen(Vec2.of(e.x(), e.y()), e.hp(), e.kind() == EntityKind.HEALTH));
      }
    }
  }

  private void advanceEffects(double dt) {
    for (Fx fx : effects) {
      fx.life -= (float) dt;
      if (fx.kind == Fx.Kind.DAMAGE) {
        fx.pos = fx.pos.sub(Vec2.of(0f, (float) (26 * dt))); // drift upward
      }
    }
    effects.removeIf(fx -> fx.life <= 0f);
    enemyFlash.replaceAll((id, remaining) -> remaining - (float) dt);
    enemyFlash.values().removeIf(remaining -> remaining <= 0f);
  }
}
