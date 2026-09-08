package kittens.server;

import java.util.concurrent.ConcurrentLinkedQueue;
import kittens.common.GameConfig;
import kittens.common.entity.Actor;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.sim.PlayerMotion;
import kittens.common.weapon.Weapon;

/**
 * The server's authoritative view of one player. Inputs land in a queue from the connection's
 * reader thread; the world-loop thread drains them each tick, applying every command through the
 * shared {@link PlayerMotion} model and remembering the last {@code seq} it processed so the client
 * can reconcile its prediction. Also owns the selected weapon, fire cooldown, health and respawn.
 */
final class ServerPlayer extends Actor {
  /** Fixed per-input time step — the client predicts with the exact same value. */
  static final double INPUT_DT = 1.0 / GameConfig.TICK_HZ;

  /** Cap catch-up so a burst of queued inputs can't teleport a player in one tick. */
  private static final int MAX_INPUTS_PER_TICK = 5;

  private final Vec2 spawn;
  private final ConcurrentLinkedQueue<InputCommand> inbox = new ConcurrentLinkedQueue<>();

  private boolean firing;
  private Weapon weapon = Weapon.PISTOL;
  private double fireCooldown;
  private double respawnTimer;
  private long lastProcessedSeq = -1;
  private Vec2 knockback = Vec2.ZERO;

  /** Rounds left in each weapon's magazine, indexed by {@link Weapon#id()}. */
  private final int[] magAmmo = new int[Weapon.count()];
  /** Seconds left on the current reload; 0 = not reloading. */
  private double reloadTimer;

  private boolean fireRequested;

  ServerPlayer(int id, Vec2 spawn) {
    super(
        id,
        spawn,
        Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE),
        GameConfig.PLAYER_MAX_HEALTH);
    this.spawn = spawn;
    refillAllMagazines();
  }

  private void refillAllMagazines() {
    for (int i = 0; i < magAmmo.length; i++) {
      magAmmo[i] = Weapon.byId(i).magazineSize;
    }
  }

  float aimAngle() {
    return (float) facing;
  }

  Weapon weapon() {
    return weapon;
  }

  boolean dead() {
    return isDead();
  }

  long lastProcessedSeq() {
    return lastProcessedSeq;
  }

  void acceptInput(InputCommand cmd) {
    inbox.add(cmd);
  }

  void tick(double dt, TileMap map) {
    fireCooldown = Math.max(0, fireCooldown - dt);
    if (reloadTimer > 0) {
      reloadTimer -= dt;
      if (reloadTimer <= 0) {
        reloadTimer = 0;
        magAmmo[weapon.id()] = weapon.magazineSize;
      }
    }

    if (dead()) {
      respawnTimer -= dt;
      if (respawnTimer <= 0) {
        respawn();
      }
      inbox.clear();
      return;
    }

    for (int applied = 0; applied < MAX_INPUTS_PER_TICK; applied++) {
      InputCommand cmd = inbox.poll();
      if (cmd == null) {
        break;
      }
      if (cmd.seq() <= lastProcessedSeq) {
        continue; // stale / duplicate
      }
      lastProcessedSeq = cmd.seq();
      facing = cmd.aimAngle();
      firing = cmd.firing();
      selectWeapon(Weapon.byId(cmd.weaponId()));
      pos = PlayerMotion.step(map, pos, cmd.moveX(), cmd.moveY(), GameConfig.PLAYER_SPEED, INPUT_DT);
    }

    // External knockback (e.g. bazooka recoil): applied on top of input, then decayed.
    if (knockback.lengthSq() > 1e-4f) {
      float kbSpeed = knockback.length();
      pos = PlayerMotion.step(
          map, pos, knockback.x / kbSpeed, knockback.y / kbSpeed, kbSpeed, dt);
    }
    knockback = knockback.scale((float) Math.max(0, 1.0 - dt * GameConfig.KNOCKBACK_DECAY));

    if (firing && fireCooldown <= 0 && reloadTimer <= 0) {
      if (magAmmo[weapon.id()] > 0) {
        fireRequested = true;
        fireCooldown = weapon.fireInterval;
        magAmmo[weapon.id()]--;
        if (magAmmo[weapon.id()] == 0) {
          reloadTimer = weapon.reloadTime; // auto-reload once the magazine runs dry
        }
      } else {
        reloadTimer = weapon.reloadTime;
      }
    }
  }

  int magAmmo() {
    return magAmmo[weapon.id()];
  }

  /** 0 when ready to fire; otherwise reload progress in (0, 1]. */
  float reloadProgress() {
    return reloadTimer <= 0 ? 0f : (float) (1.0 - reloadTimer / weapon.reloadTime);
  }

  /** Shove this player by {@code force} (px/s); decays over the next few ticks. */
  void applyKnockback(Vec2 force) {
    knockback = knockback.add(force);
  }

  private void selectWeapon(Weapon next) {
    if (next == weapon) {
      return;
    }
    weapon = next;
    reloadTimer = 0; // switching cancels an in-progress reload
    // Switching can't fire sooner than the new weapon allows, but also can't be gamed to skip an
    // already-shorter cooldown.
    fireCooldown = Math.min(fireCooldown, next.fireInterval);
  }

  /** Returns whether the player wants to fire this tick, clearing the request. */
  boolean consumeFireRequest() {
    boolean r = fireRequested;
    fireRequested = false;
    return r;
  }

  @Override
  public void damage(double amount) {
    if (dead() || amount <= 0) {
      return;
    }
    super.damage(amount);
    if (dead()) {
      firing = false;
      respawnTimer = GameConfig.RESPAWN_DELAY;
    }
  }

  private void respawn() {
    pos = spawn;
    health = maxHealth;
    alive = true;
    reloadTimer = 0;
    refillAllMagazines();
  }

  EntityState toEntityState() {
    return new EntityState(id, "cat", pos.x, pos.y, (float) facing, (float) health, weapon.id());
  }
}
