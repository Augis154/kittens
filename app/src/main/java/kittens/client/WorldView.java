package kittens.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import kittens.client.events.GameEvent;
import kittens.client.events.GameEvents;
import kittens.common.GameConfig;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;
import kittens.common.weapon.Weapon;
import kittens.common.weapon.WeaponFactory;

/**
 * Cosmetic smoothing of everything the local player does <em>not</em> control: remote actors are
 * eased toward their snapshot positions and bullets are dead-reckoned between snapshots. It is also
 * where the client notices what happened — a health drop, a vanished pickup, a cracked crate, a new
 * projectile — by diffing snapshots, and publishes each as a {@link GameEvent} for {@link Effects}
 * and {@link SoundObserver} to react to. Nothing here feeds prediction, so it can be retuned freely
 * without desync risk.
 */
final class WorldView {
  private static final float REMOTE_SMOOTHING = 0.30f;
  private static final float BULLET_SMOOTHING = 0.25f;
  private static final float BULLET_SNAP = 48f;
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

  private record PickupSeen(Vec2 pos, float life, EntityKind kind) {}

  private final GameEvents events;
  private final Map<Integer, Vec2> smoothed = new HashMap<>();
  private final Map<Integer, Bullet> bullets = new HashMap<>();
  private final Map<Integer, PickupSeen> pickupSeen = new HashMap<>();
  private final Map<Integer, EntityState> enemyLast = new HashMap<>();
  private final Map<Integer, EntityState> crateLast = new HashMap<>();
  private final Set<Integer> blastsHeard = new HashSet<>();

  WorldView(GameEvents events) {
    this.events = events;
  }

  void update(double dt, Map<Integer, EntityState> entities, int myPlayerId) {
    smoothRemotes(entities, myPlayerId);
    updateBullets(dt, entities);
    trackEnemyDamage(entities);
    trackPickups(entities);
    trackCrates(entities);
    trackBlasts(entities);
  }

  /** Smoothed position of an actor, falling back to its snapshot position. */
  Vec2 positionOf(EntityState e) {
    Vec2 p = smoothed.get(e.id());
    return p != null ? p : Vec2.of(e.x(), e.y());
  }

  Collection<Bullet> bullets() {
    return bullets.values();
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
      Weapon weapon = WeaponFactory.decode(e.weaponId());
      Vec2 authority = Vec2.of(e.x(), e.y());
      Bullet b = bullets.get(e.id());
      if (b == null) {
        bullets.put(e.id(), new Bullet(authority, e.angle(), weapon));
        // First sighting: it left the barrel a tick ago. Shotgun pellets share a muzzle, so their
        // overlapping flashes read as one bigger blast.
        float back = (float) (weapon.projectileSpeed() * GameConfig.TICK_DT);
        Vec2 origin = authority.sub(Vec2.fromAngle(e.angle()).scale(back));
        events.publish(new GameEvent.ShotFired(origin, authority, e.angle(), weapon,
            WeaponFactory.slotOf(e.weaponId())));
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

  /**
   * A health drop between snapshots is one hit, published once; an enemy dropping out of the
   * snapshot was killed, and is reported where it was last seen.
   */
  private void trackEnemyDamage(Map<Integer, EntityState> live) {
    Iterator<Map.Entry<Integer, EntityState>> it = enemyLast.entrySet().iterator();
    while (it.hasNext()) {
      EntityState gone = it.next().getValue();
      if (!live.containsKey(gone.id())) {
        events.publish(new GameEvent.EnemyKilled(Vec2.of(gone.x(), gone.y())));
        it.remove();
      }
    }
    for (EntityState e : live.values()) {
      if (!e.kind().isEnemy()) {
        continue;
      }
      EntityState previous = enemyLast.put(e.id(), e);
      if (previous == null || e.hp() >= previous.hp()) {
        continue;
      }
      events.publish(new GameEvent.EnemyHit(e.id(), positionOf(e),
          Math.max(1, Math.round(previous.hp() - e.hp()))));
    }
  }

  /** A pickup vanishing with life to spare was walked into by somebody. */
  private void trackPickups(Map<Integer, EntityState> live) {
    Iterator<Map.Entry<Integer, PickupSeen>> it = pickupSeen.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Integer, PickupSeen> entry = it.next();
      if (live.containsKey(entry.getKey())) {
        continue;
      }
      PickupSeen last = entry.getValue();
      if (last.life() > PICKUP_TAKEN_LIFE) {
        events.publish(new GameEvent.PickupCollected(last.pos(), last.kind()));
      }
      it.remove();
    }
    for (EntityState e : live.values()) {
      if (e.kind().isPickup()) {
        pickupSeen.put(e.id(), new PickupSeen(Vec2.of(e.x(), e.y()), e.hp(), e.kind()));
      }
    }
  }

  /** Crates never expire, so one leaving the snapshot was shot open; any health drop was a hit. */
  private void trackCrates(Map<Integer, EntityState> live) {
    Iterator<Map.Entry<Integer, EntityState>> it = crateLast.entrySet().iterator();
    while (it.hasNext()) {
      EntityState gone = it.next().getValue();
      if (!live.containsKey(gone.id())) {
        events.publish(new GameEvent.CrateBroken(Vec2.of(gone.x(), gone.y())));
        it.remove();
      }
    }
    for (EntityState e : live.values()) {
      if (e.kind() != EntityKind.CRATE) {
        continue;
      }
      EntityState previous = crateLast.put(e.id(), e);
      if (previous != null && e.hp() < previous.hp()) {
        events.publish(new GameEvent.CrateHit(e.id(), Vec2.of(e.x(), e.y())));
      }
    }
  }

  /** Each explosion is published once, on the snapshot it first appears in. */
  private void trackBlasts(Map<Integer, EntityState> live) {
    blastsHeard.removeIf(id -> !live.containsKey(id));
    for (EntityState e : live.values()) {
      if (e.kind() == EntityKind.BOOM && blastsHeard.add(e.id())) {
        events.publish(new GameEvent.Exploded(Vec2.of(e.x(), e.y())));
      }
    }
  }
}
