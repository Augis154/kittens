package kittens.server;

import java.util.concurrent.ConcurrentLinkedQueue;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.sim.PlayerMotion;

/**
 * The server's authoritative view of one player. Inputs land in a queue from the connection's
 * reader thread; the world-loop thread drains them each tick, applying every command through the
 * shared {@link PlayerMotion} model and remembering the last {@code seq} it processed so the client
 * can reconcile its prediction.
 */
final class ServerPlayer {
  /** Fixed per-input time step — the client predicts with the exact same value. */
  static final double INPUT_DT = 1.0 / GameConfig.TICK_HZ;

  /** Cap catch-up so a burst of queued inputs can't teleport a player in one tick. */
  private static final int MAX_INPUTS_PER_TICK = 5;

  private final int id;
  private final ConcurrentLinkedQueue<InputCommand> inbox = new ConcurrentLinkedQueue<>();

  private Vec2 pos;
  private float aimAngle;
  private double health = 100;
  private long lastProcessedSeq = -1;

  ServerPlayer(int id, Vec2 spawn) {
    this.id = id;
    this.pos = spawn;
  }

  int id() {
    return id;
  }

  long lastProcessedSeq() {
    return lastProcessedSeq;
  }

  void acceptInput(InputCommand cmd) {
    inbox.add(cmd);
  }

  void tick(TileMap map) {
    for (int applied = 0; applied < MAX_INPUTS_PER_TICK; applied++) {
      InputCommand cmd = inbox.poll();
      if (cmd == null) {
        break;
      }
      if (cmd.seq() <= lastProcessedSeq) {
        continue; // stale / duplicate
      }
      lastProcessedSeq = cmd.seq();
      aimAngle = cmd.aimAngle();
      pos = PlayerMotion.step(map, pos, cmd.moveX(), cmd.moveY(), GameConfig.PLAYER_SPEED, INPUT_DT);
    }
  }

  EntityState toEntityState() {
    return new EntityState(id, "cat", pos.x, pos.y, aimAngle, (float) health, -1);
  }
}
