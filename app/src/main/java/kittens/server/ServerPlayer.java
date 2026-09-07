package kittens.server;

import java.util.concurrent.ConcurrentLinkedQueue;
import kittens.common.GameConfig;
import kittens.common.entity.Actor;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.sim.PlayerMotion;

/**
 * The server's authoritative view of one player. Inputs land in a queue from the connection's
 * reader thread; the world-loop thread drains them each tick, applying every command through the
 * shared {@link PlayerMotion} model and remembering the last {@code seq} it processed so the client
 * can reconcile its prediction. Also owns the fire cooldown, health and respawn timer.
 */
final class ServerPlayer extends Actor {
  /** Fixed per-input time step — the client predicts with the exact same value. */
  static final double INPUT_DT = 1.0 / GameConfig.TICK_HZ;

  /** Cap catch-up so a burst of queued inputs can't teleport a player in one tick. */
  private static final int MAX_INPUTS_PER_TICK = 5;

  private final Vec2 spawn;
  private final ConcurrentLinkedQueue<InputCommand> inbox = new ConcurrentLinkedQueue<>();

  private boolean firing;
  private double fireCooldown;
  private double respawnTimer;
  private long lastProcessedSeq = -1;

  private boolean fireRequested;

  ServerPlayer(int id, Vec2 spawn) {
    super(
        id,
        spawn,
        Vec2.of(GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE),
        GameConfig.PLAYER_MAX_HEALTH);
    this.spawn = spawn;
  }

  float aimAngle() {
    return (float) facing;
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
      pos = PlayerMotion.step(map, pos, cmd.moveX(), cmd.moveY(), GameConfig.PLAYER_SPEED, INPUT_DT);
    }

    if (firing && fireCooldown <= 0) {
      fireRequested = true;
      fireCooldown = GameConfig.FIRE_INTERVAL;
    }
  }

  /** Returns whether the player wants to fire a projectile this tick, clearing the request. */
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
  }

  EntityState toEntityState() {
    return new EntityState(id, "cat", pos.x, pos.y, (float) facing, (float) health, -1);
  }
}
