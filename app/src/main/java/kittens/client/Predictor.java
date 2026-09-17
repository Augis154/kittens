package kittens.client;

import java.util.ArrayDeque;
import java.util.Deque;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.sim.Motion;

/**
 * Client-side prediction of the local kitten: input moves it immediately, and every new snapshot
 * re-anchors it to the authoritative position with the un-acked inputs replayed on top, easing
 * small errors and snapping past a threshold. Uses the same {@link Motion} and
 * {@link GameConfig#TICK_DT} as the server — that equality is what keeps it from rubber-banding.
 */
final class Predictor {
  private static final float SMOOTHING = 0.25f;
  private static final float SNAP = 64f;
  private static final int MAX_PENDING = 4 * GameConfig.TICK_HZ; // ~4 s safety cap

  private record Pending(long seq, float moveX, float moveY) {}

  private final TileMap map;
  private final Deque<Pending> unacked = new ArrayDeque<>();
  private Vec2 predicted; // null until the first snapshot containing our kitten
  private long lastTick = -1;

  Predictor(TileMap map) {
    this.map = map;
  }

  Vec2 position() {
    return predicted;
  }

  void remember(InputCommand cmd) {
    unacked.addLast(new Pending(cmd.seq(), cmd.moveX(), cmd.moveY()));
    while (unacked.size() > MAX_PENDING) {
      unacked.pollFirst();
    }
  }

  /** Per frame: reconcile if a new snapshot arrived, then advance by this frame's input. */
  void update(double dt, GameClient.Frame frame, EntityState me, float moveX, float moveY) {
    if (me == null) {
      return;
    }
    if (frame.tick() != lastTick) {
      lastTick = frame.tick();
      reconcile(frame.ackSeq(), me);
    }
    if (predicted != null && dt > 0 && me.hp() > 0f) {
      predicted = Motion.step(map, predicted, moveX, moveY, GameConfig.PLAYER_SPEED, dt);
    }
  }

  private void reconcile(long ackSeq, EntityState me) {
    Vec2 authority = Vec2.of(me.x(), me.y());
    if (me.hp() <= 0f) {
      unacked.clear(); // downed: pin to the server's frozen position
      predicted = authority;
      return;
    }
    while (!unacked.isEmpty() && unacked.peekFirst().seq() <= ackSeq) {
      unacked.pollFirst();
    }
    Vec2 target = authority;
    for (Pending p : unacked) {
      target = Motion.step(map, target, p.moveX(), p.moveY(), GameConfig.PLAYER_SPEED,
          GameConfig.TICK_DT);
    }
    if (predicted == null || predicted.distance(target) > SNAP) {
      predicted = target;
    } else {
      predicted = predicted.add(target.sub(predicted).scale(SMOOTHING));
    }
  }
}
