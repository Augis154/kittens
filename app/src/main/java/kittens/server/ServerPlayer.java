package kittens.server;

import java.util.concurrent.ConcurrentLinkedQueue;
import kittens.common.GameConfig;
import kittens.common.entity.Actor;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.Buff;
import kittens.common.net.BuffState;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.net.PlayerInfo;
import kittens.common.sim.Motion;
import kittens.common.weapon.Arsenal;
import kittens.common.weapon.WeaponFactory;

/**
 * The server's authoritative player. Inputs are queued by the connection's reader thread and
 * drained on the loop thread, each applied through the shared {@link Motion} model with
 * {@link GameConfig#TICK_DT} so the client can replay them identically.
 */
final class ServerPlayer extends Actor {
  /** Cap on catch-up so a burst of queued inputs can't teleport a player in one tick. */
  private static final int MAX_INPUTS_PER_TICK = 5;

  private final Vec2 spawn;
  private final ConcurrentLinkedQueue<InputCommand> inbox = new ConcurrentLinkedQueue<>();
  private final Arsenal arsenal;

  private boolean firing;
  private boolean fireRequested;
  private double respawnTimer;
  /** Spawn grace and the post-hit i-frame share this one timer. */
  private double invulnerableTimer = GameConfig.RESPAWN_INVULNERABILITY;
  private long lastProcessedSeq = -1;
  // Pickup buffs. All are lost when the kitten goes down.
  private double doubleDamageTimer;
  private double catnipTimer;
  private double shieldPoints;
  private double shieldTimer;
  private boolean nineLives;
  /**
   * The one field written off the loop thread: a reader thread replaces it on a profile change, and
   * the loop reads it into each snapshot. Swapped whole, so a reader never sees a new name with an old
   * colour.
   */
  private volatile PlayerInfo info;

  ServerPlayer(int id, Vec2 spawn, WeaponFactory family, String name, String color) {
    super(id, spawn, Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE),
        GameConfig.PLAYER_MAX_HEALTH);
    this.spawn = spawn;
    this.arsenal = family.createArsenal();
    setProfile(name, color);
  }

  PlayerInfo info() {
    return info;
  }

  /** Untrusted input off the wire: the name is cleaned and the colour resolved here. */
  void setProfile(String name, String color) {
    info = new PlayerInfo(id, GameConfig.cleanName(name), GameConfig.kittenColor(color, id));
  }

  float aimAngle() {
    return (float) facing;
  }

  Arsenal arsenal() {
    return arsenal;
  }

  long lastProcessedSeq() {
    return lastProcessedSeq;
  }

  /** Called from the reader thread. */
  void acceptInput(InputCommand cmd) {
    inbox.add(cmd);
  }

  void tick(double dt, TileMap map) {
    invulnerableTimer = Math.max(0, invulnerableTimer - dt);
    doubleDamageTimer = Math.max(0, doubleDamageTimer - dt);
    catnipTimer = Math.max(0, catnipTimer - dt);
    shieldTimer = Math.max(0, shieldTimer - dt);
    if (shieldTimer <= 0) {
      shieldPoints = 0;
    }
    // Catnip runs the arsenal's clock at double speed: cooldowns and reloads both halve.
    arsenal.tick(catnipTimer > 0 ? dt * 2 : dt);

    if (!isAlive()) {
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
        continue; // stale or duplicate
      }
      lastProcessedSeq = cmd.seq();
      facing = cmd.aimAngle();
      firing = cmd.firing();
      arsenal.select(cmd.slot());
      if (cmd.reload()) {
        arsenal.requestReload();
      }
      pos = Motion.step(map, pos, cmd.moveX(), cmd.moveY(), GameConfig.PLAYER_SPEED,
          GameConfig.TICK_DT);
    }

    // Knockback (bazooka recoil) rides on top of the input-driven movement.
    if (knockback.lengthSq() > 1e-4f) {
      float speed = knockback.length();
      pos = Motion.step(map, pos, knockback.x / speed, knockback.y / speed, speed, dt);
    }
    decayKnockback(dt);

    if (firing && arsenal.tryFire()) {
      fireRequested = true;
    }
  }

  /** Whether the player wants to fire this tick; clears the request. */
  boolean consumeFireRequest() {
    boolean r = fireRequested;
    fireRequested = false;
    return r;
  }

  boolean wantsHealth() {
    return isAlive() && health < maxHealth;
  }

  /** What this kitten's shots are multiplied by: 2 under Double Damage. */
  double damageScale() {
    return doubleDamageTimer > 0 ? 2.0 : 1.0;
  }

  /** Seconds left on a timed buff; 0 for one that is not running or not timed. */
  double buffLeft(Buff buff) {
    return switch (buff) {
      case DOUBLE_DAMAGE -> doubleDamageTimer;
      case CATNIP -> catnipTimer;
      case SHIELD -> shieldTimer;
      case NINE_LIVES -> 0;
    };
  }

  /** Starts or refreshes a timed buff. */
  void grant(Buff buff, double seconds) {
    switch (buff) {
      case DOUBLE_DAMAGE -> doubleDamageTimer = seconds;
      case CATNIP -> catnipTimer = seconds;
      default -> throw new IllegalArgumentException(buff + " is not a plain timed buff");
    }
  }

  double shieldPoints() {
    return shieldPoints;
  }

  void grantShield(double points, double seconds) {
    shieldPoints = points;
    shieldTimer = seconds;
  }

  boolean hasNineLives() {
    return nineLives;
  }

  void grantNineLives() {
    nineLives = true;
  }

  /**
   * After spawn grace and the i-frame, a shield soaks the hit first; a blow the shield did not cover
   * that would down the kitten spends Nine Lives instead, leaving it standing with fresh grace.
   */
  @Override
  public void damage(double amount) {
    if (!isAlive() || invulnerableTimer > 0 || amount <= 0) {
      return;
    }
    double soaked = Math.min(shieldPoints, amount);
    shieldPoints -= soaked;
    amount -= soaked;
    if (shieldPoints <= 0) {
      shieldTimer = 0;
    }
    if (amount > 0 && amount >= health && nineLives) {
      nineLives = false;
      health = GameConfig.NINE_LIVES_HEALTH;
      invulnerableTimer = GameConfig.RESPAWN_INVULNERABILITY;
      return;
    }
    super.damage(amount);
    if (!isAlive()) {
      firing = false;
      respawnTimer = GameConfig.RESPAWN_DELAY;
      clearBuffs();
    } else {
      invulnerableTimer = Math.max(invulnerableTimer, GameConfig.HIT_INVULNERABILITY);
    }
  }

  private void clearBuffs() {
    doubleDamageTimer = 0;
    catnipTimer = 0;
    shieldPoints = 0;
    shieldTimer = 0;
    nineLives = false;
  }

  private int buffMask() {
    int mask = 0;
    if (doubleDamageTimer > 0) {
      mask |= Buff.DOUBLE_DAMAGE.bit();
    }
    if (catnipTimer > 0) {
      mask |= Buff.CATNIP.bit();
    }
    if (shieldPoints > 0) {
      mask |= Buff.SHIELD.bit();
    }
    if (nineLives) {
      mask |= Buff.NINE_LIVES.bit();
    }
    return mask;
  }

  /** This kitten's own buff detail, for its HUD. */
  BuffState buffState() {
    return new BuffState((float) doubleDamageTimer, (float) catnipTimer, (float) shieldPoints,
        (float) shieldTimer, nineLives);
  }

  private void respawn() {
    pos = spawn;
    health = maxHealth;
    alive = true;
    arsenal.restock();
    invulnerableTimer = GameConfig.RESPAWN_INVULNERABILITY;
  }

  EntityState toEntityState() {
    return new EntityState(id, EntityKind.CAT, pos.x, pos.y, (float) facing, (float) health,
        arsenal.wireId(), (float) invulnerableTimer, buffMask());
  }
}
