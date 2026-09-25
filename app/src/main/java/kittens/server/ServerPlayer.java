package kittens.server;

import java.util.concurrent.ConcurrentLinkedQueue;
import kittens.common.GameConfig;
import kittens.common.entity.Actor;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
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
  private final Arsenal arsenal = WeaponFactory.newArsenal();

  private boolean firing;
  private boolean fireRequested;
  private double respawnTimer;
  /** Spawn grace and the post-hit i-frame share this one timer. */
  private double invulnerableTimer = GameConfig.RESPAWN_INVULNERABILITY;
  private long lastProcessedSeq = -1;

  ServerPlayer(int id, Vec2 spawn) {
    super(id, spawn, Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE),
        GameConfig.PLAYER_MAX_HEALTH);
    this.spawn = spawn;
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
    arsenal.tick(dt);

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
      arsenal.select(cmd.weaponId());
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

  @Override
  public void damage(double amount) {
    if (!isAlive() || invulnerableTimer > 0) {
      return;
    }
    super.damage(amount);
    if (!isAlive()) {
      firing = false;
      respawnTimer = GameConfig.RESPAWN_DELAY;
    } else {
      invulnerableTimer = Math.max(invulnerableTimer, GameConfig.HIT_INVULNERABILITY);
    }
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
        arsenal.selectedId(), (float) invulnerableTimer);
  }
}
