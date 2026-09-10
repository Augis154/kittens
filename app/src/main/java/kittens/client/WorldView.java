package kittens.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kittens.common.GameConfig;
import kittens.common.net.EntityState;
import kittens.common.weapon.Weapon;

/**
 * Presentation-side smoothing of everything the local player does <em>not</em> control: remote
 * kittens and enemies are eased toward their snapshot positions, projectiles are dead-reckoned
 * between snapshots, and health drops are turned into hit flashes and floating damage numbers.
 *
 * <p>None of this is authoritative and none of it feeds prediction — {@link Client} owns the local
 * player's predicted position, which is reconciled against the server rather than smoothed. This
 * class exists purely so {@link Renderer} has something to draw between the 30 Hz snapshots, so it
 * may be retuned freely; nothing here can desync the simulation.
 */
final class WorldView {
  /** Per-frame easing of remote entities toward their snapshot position. */
  private static final float REMOTE_SMOOTHING = 0.30f;

  /** Per-frame easing of a projectile toward its snapshot position. */
  private static final float BULLET_SMOOTHING = 0.25f;

  /** Distance (px) past which a projectile snaps to authority instead of easing toward it. */
  private static final float BULLET_SNAP = 48f;

  /** Seconds an enemy stays lit up after taking a hit. */
  private static final float HIT_FLASH_SECONDS = 0.14f;

  /** One server tick — how stale a projectile's first snapshot already is when we see it. */
  private static final double TICK_DT = 1.0 / GameConfig.TICK_HZ;

  /** A short-lived cosmetic effect in world space. Client-only: never sent, never simulated. */
  static final class Fx {
    enum Kind {
      /** Barrel flash where a round was fired. */
      MUZZLE,
      /** Expanding ring on an enemy that just took damage. */
      HIT,
      /** Floating damage number drifting off the enemy that took the hit. */
      DAMAGE
    }

    private final Kind kind;
    private float x;
    private float y;
    private final float angle;
    private final int value;
    private final float maxLife;
    private float life;

    private Fx(Kind kind, float x, float y, float angle, int value, float life) {
      this.kind = kind;
      this.x = x;
      this.y = y;
      this.angle = angle;
      this.value = value;
      this.maxLife = life;
      this.life = life;
    }

    Kind kind() {
      return kind;
    }

    float x() {
      return x;
    }

    float y() {
      return y;
    }

    float angle() {
      return angle;
    }

    /** Damage amount for {@link Kind#DAMAGE}; 1 marks an explosive muzzle flash. */
    int value() {
      return value;
    }

    /** 1 when the effect has just started, easing to 0 as it expires. */
    float progress() {
      return Math.clamp(life / maxLife, 0f, 1f);
    }
  }

  // Remote entities (players & enemies): interpolated on-screen position per id -> {x, y}.
  private final Map<Integer, float[]> remotePos = new HashMap<>();

  // Client-simulated projectiles: id -> {x, y, angle, vx, vy, weaponId} for smooth flight.
  private final Map<Integer, float[]> bullets = new HashMap<>();

  // Hit feedback: last health seen per enemy, and how long each one stays lit after losing some.
  private final Map<Integer, Float> enemyHp = new HashMap<>();
  private final Map<Integer, Float> enemyFlash = new HashMap<>();
  private final List<Fx> effects = new ArrayList<>();

  /** Advances every smoothed value and effect by one client frame. */
  void update(double dt, Map<Integer, EntityState> entities, int myPlayerId) {
    interpolateRemotes(entities, myPlayerId);
    updateBullets(dt, entities);
    trackEnemyDamage(entities);
    advanceEffects(dt);
  }

  /** Smoothed on-screen position of an entity as {@code {x, y}}, or null if it is unknown yet. */
  float[] smoothed(int id) {
    return remotePos.get(id);
  }

  /** Live projectiles as {@code {x, y, angle, vx, vy, weaponId}}. */
  Collection<float[]> bullets() {
    return bullets.values();
  }

  /** How lit an enemy is from a recent hit: 1 right after it lands, easing to 0. */
  float hitFlash(int id) {
    return Math.clamp(enemyFlash.getOrDefault(id, 0f) / HIT_FLASH_SECONDS, 0f, 1f);
  }

  List<Fx> effects() {
    return effects;
  }

  private void interpolateRemotes(Map<Integer, EntityState> live, int myPlayerId) {
    remotePos.keySet().removeIf(id -> id == myPlayerId || !live.containsKey(id));
    for (EntityState e : live.values()) {
      if (e.id() == myPlayerId || "bullet".equals(e.kind()) || "boom".equals(e.kind())) {
        continue; // bullets are dead-reckoned below, booms are drawn straight from the snapshot
      }
      float[] rp = remotePos.get(e.id());
      if (rp == null) {
        remotePos.put(e.id(), new float[] {e.x(), e.y()});
      } else {
        rp[0] += (e.x() - rp[0]) * REMOTE_SMOOTHING;
        rp[1] += (e.y() - rp[1]) * REMOTE_SMOOTHING;
      }
    }
  }

  /** Flies projectiles forward every client frame and reconciles them with server snapshots. */
  private void updateBullets(double dt, Map<Integer, EntityState> live) {
    // 1. Drop bullets the server no longer reports.
    bullets.keySet().removeIf(id -> {
      EntityState e = live.get(id);
      return e == null || !"bullet".equals(e.kind());
    });

    // 2. Synchronize new/existing bullets with server snapshot authority.
    for (EntityState e : live.values()) {
      if (!"bullet".equals(e.kind())) {
        continue;
      }
      Weapon weapon = Weapon.byId(e.weaponId());
      float speed = weapon.projectileSpeed;
      float vx = (float) Math.cos(e.angle()) * speed;
      float vy = (float) Math.sin(e.angle()) * speed;

      float[] b = bullets.get(e.id());
      if (b == null) {
        // [x, y, angle, vx, vy, weaponId]
        bullets.put(e.id(), new float[] {e.x(), e.y(), e.angle(), vx, vy, e.weaponId()});
        // First sighting of a round: flash roughly where it left the barrel a tick ago. Shotgun
        // pellets share a muzzle, so the overlapping flashes read as one bigger blast.
        float back = (float) (speed * TICK_DT);
        effects.add(
            new Fx(
                Fx.Kind.MUZZLE,
                e.x() - (float) Math.cos(e.angle()) * back,
                e.y() - (float) Math.sin(e.angle()) * back,
                e.angle(),
                weapon.explosive() ? 1 : 0,
                0.09f));
      } else {
        float dx = e.x() - b[0];
        float dy = e.y() - b[1];
        if (dx * dx + dy * dy > BULLET_SNAP * BULLET_SNAP) {
          b[0] = e.x();
          b[1] = e.y();
        } else {
          b[0] += dx * BULLET_SMOOTHING;
          b[1] += dy * BULLET_SMOOTHING;
        }
        b[2] = e.angle();
        b[3] = vx;
        b[4] = vy;
        b[5] = e.weaponId();
      }
    }

    // 3. Extrapolate forward for this client frame.
    if (dt > 0) {
      for (float[] b : bullets.values()) {
        b[0] += b[3] * (float) dt;
        b[1] += b[4] * (float) dt;
      }
    }
  }

  /**
   * Snapshot-to-snapshot health deltas drive all hit feedback: a flash on the enemy plus a floating
   * damage number. Comparing against the last value seen makes this idempotent, so running it every
   * frame (rather than only on a new snapshot) reports each hit exactly once.
   */
  private void trackEnemyDamage(Map<Integer, EntityState> live) {
    enemyHp.keySet().removeIf(id -> !live.containsKey(id));
    enemyFlash.keySet().removeIf(id -> !live.containsKey(id));
    for (EntityState e : live.values()) {
      if (!"rat".equals(e.kind()) && !"mouse".equals(e.kind())) {
        continue;
      }
      Float previous = enemyHp.put(e.id(), e.hp());
      if (previous == null || e.hp() >= previous) {
        continue;
      }
      float[] pos = remotePos.get(e.id());
      float x = pos != null ? pos[0] : e.x();
      float y = pos != null ? pos[1] : e.y();
      enemyFlash.put(e.id(), HIT_FLASH_SECONDS);
      effects.add(new Fx(Fx.Kind.HIT, x, y, 0f, 0, 0.22f));
      effects.add(
          new Fx(Fx.Kind.DAMAGE, x, y - GameConfig.TILE * 0.4f, 0f,
              Math.max(1, Math.round(previous - e.hp())), 0.7f));
    }
  }

  private void advanceEffects(double dt) {
    for (Fx fx : effects) {
      fx.life -= (float) dt;
      if (fx.kind == Fx.Kind.DAMAGE) {
        fx.y -= (float) (26 * dt); // drift upward off the enemy that took the hit
      }
    }
    effects.removeIf(fx -> fx.life <= 0f);
    enemyFlash.replaceAll((id, remaining) -> remaining - (float) dt);
    enemyFlash.values().removeIf(remaining -> remaining <= 0f);
  }
}
