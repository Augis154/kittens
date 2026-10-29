package kittens.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kittens.client.events.GameEvent;
import kittens.client.events.GameObserver;
import kittens.common.GameConfig;
import kittens.common.math.Vec2;

/**
 * The visual half of the client's reaction to game events (an Observer): muzzle flashes, hit sparks,
 * floating damage numbers, pickup rings and the white flash on a struck enemy. Client-only: never
 * sent, never simulated.
 */
final class Effects implements GameObserver {
  private static final float HIT_FLASH_SECONDS = 0.14f;

  /** A short-lived world-space effect. */
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

  private final Map<Integer, Float> enemyFlash = new HashMap<>();
  private final List<Fx> active = new ArrayList<>();

  @Override
  public void onEvent(GameEvent event) {
    switch (event) {
      case GameEvent.ShotFired s ->
          active.add(new Fx(Fx.Kind.MUZZLE, s.origin(), s.angle(), 0, s.weapon().explosive(), 0.09f));
      case GameEvent.EnemyHit h -> {
        enemyFlash.put(h.enemyId(), HIT_FLASH_SECONDS);
        active.add(new Fx(Fx.Kind.HIT, h.at(), 0f, 0, false, 0.22f));
        active.add(new Fx(Fx.Kind.DAMAGE, h.at().sub(Vec2.of(0f, GameConfig.TILE * 0.4f)), 0f,
            h.damage(), false, 0.7f));
      }
      case GameEvent.PickupCollected p ->
          active.add(new Fx(Fx.Kind.PICKUP, p.at(), 0f, 0, p.health(), 0.34f));
      default -> { } // the rest are for the ear, not the eye
    }
  }

  /** Ages every effect by {@code dt}; call once per frame, after the frame's events are in. */
  void advance(double dt) {
    for (Fx fx : active) {
      fx.life -= (float) dt;
      if (fx.kind == Fx.Kind.DAMAGE) {
        fx.pos = fx.pos.sub(Vec2.of(0f, (float) (26 * dt))); // drift upward
      }
    }
    active.removeIf(fx -> fx.life <= 0f);
    enemyFlash.replaceAll((id, remaining) -> remaining - (float) dt);
    enemyFlash.values().removeIf(remaining -> remaining <= 0f);
  }

  /** How lit an enemy is from a recent hit: 1 as it lands, easing to 0. */
  float hitFlash(int id) {
    return Math.clamp(enemyFlash.getOrDefault(id, 0f) / HIT_FLASH_SECONDS, 0f, 1f);
  }

  List<Fx> active() {
    return active;
  }
}
