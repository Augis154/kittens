package kittens.client;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.net.Join;
import kittens.common.net.JoinAccepted;
import kittens.common.net.Message;
import kittens.common.net.MessageChannel;
import kittens.common.net.Snapshot;

/**
 * The client's network half: joins, streams {@link InputCommand}s, and publishes the latest
 * snapshot as one immutable {@link Frame} so the EDT always reads a consistent picture.
 */
final class GameClient {
  /** One received snapshot, indexed by entity id. */
  record Frame(long tick, long ackSeq, int ammo, float reload, Map<Integer, EntityState> entities) {
    static final Frame EMPTY = new Frame(-1, -1, 0, 0f, Map.of());

    static Frame of(Snapshot s) {
      Map<Integer, EntityState> byId = new LinkedHashMap<>();
      for (EntityState e : s.entities()) {
        byId.put(e.id(), e);
      }
      return new Frame(s.tick(), s.ackSeq(), s.viewerAmmo(), s.viewerReload(),
          Collections.unmodifiableMap(byId));
    }

    /** The entity with this id, or {@code null}. */
    EntityState entity(int id) {
      return entities.get(id);
    }
  }

  private final MessageChannel channel;
  private long inputSeq; // EDT only
  private volatile int myPlayerId = -1;
  private volatile Frame latest = Frame.EMPTY;

  GameClient(String host, int port) throws IOException {
    channel = MessageChannel.connect(host, port);
  }

  void start() {
    channel.send(new Join("kitten"));
    Thread reader = new Thread(() -> channel.readLoop(this::handle), "client-net");
    reader.setDaemon(true);
    reader.start();
  }

  /** -1 until the server accepts the join. */
  int myPlayerId() {
    return myPlayerId;
  }

  Frame latest() {
    return latest;
  }

  /** Sends the current intent; the returned command's {@code seq} is what the predictor remembers. */
  InputCommand sendInput(float moveX, float moveY, float aimAngle, boolean firing, int weaponId,
      boolean reload) {
    InputCommand cmd = new InputCommand(moveX, moveY, aimAngle, firing, weaponId, reload, inputSeq++);
    channel.send(cmd);
    return cmd;
  }

  private void handle(Message message) {
    switch (message) {
      case JoinAccepted accepted -> myPlayerId = accepted.playerId();
      case Snapshot snapshot -> latest = Frame.of(snapshot);
      default -> {} // Join / InputCommand only travel the other way
    }
  }
}
