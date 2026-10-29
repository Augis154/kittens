package kittens.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kittens.client.settings.Profile;
import kittens.client.settings.Settings;
import kittens.common.map.TileMap;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.net.Join;
import kittens.common.net.JoinAccepted;
import kittens.common.net.Message;
import kittens.common.net.PlayerInfo;
import kittens.common.net.ProfileUpdate;
import kittens.common.net.Snapshot;
import kittens.common.net.Transport;
import kittens.common.weapon.WeaponFactory;

/**
 * The client's network half: joins, streams {@link InputCommand}s, and publishes the latest
 * snapshot as one immutable {@link Frame} so the EDT always reads a consistent picture. It talks
 * through a {@link Transport} and cannot tell a server across the network from one in this process.
 */
final class GameClient {
  /** One received snapshot, with entities and the player roster indexed by id. */
  record Frame(long tick, long ackSeq, int ammo, float reload, int wave, float intermission,
      Map<Integer, EntityState> entities, Map<Integer, PlayerInfo> players) {
    static final Frame EMPTY = new Frame(-1, -1, 0, 0f, 0, 0f, Map.of(), Map.of());

    static Frame of(Snapshot s) {
      Map<Integer, EntityState> byId = new LinkedHashMap<>();
      for (EntityState e : s.entities()) {
        byId.put(e.id(), e);
      }
      Map<Integer, PlayerInfo> roster = new HashMap<>();
      for (PlayerInfo p : s.players()) {
        roster.put(p.id(), p);
      }
      return new Frame(s.tick(), s.ackSeq(), s.viewerAmmo(), s.viewerReload(), s.wave(),
          s.intermission(), Collections.unmodifiableMap(byId), Collections.unmodifiableMap(roster));
    }

    /** The entity with this id, or {@code null}. */
    EntityState entity(int id) {
      return entities.get(id);
    }
  }

  private final Transport channel;
  private long inputSeq; // EDT only
  private volatile int myPlayerId = -1;
  private volatile Frame latest = Frame.EMPTY;
  /** Published once, by the reader thread, when the server's level arrives. */
  private volatile TileMap map;
  private final CountDownLatch mapLatch = new CountDownLatch(1);

  GameClient(Transport channel) {
    this.channel = channel;
  }

  void start(WeaponFactory family) {
    Profile me = Settings.get().profile();
    channel.send(new Join(me.name(), family.name(), me.color()));
    Thread reader = new Thread(() -> channel.readLoop(this::handle), "client-net");
    reader.setDaemon(true);
    reader.start();
  }

  /** Tells the server the player's current name and colour; it shows on everyone's next tick. */
  void sendProfile() {
    Profile me = Settings.get().profile();
    channel.send(new ProfileUpdate(me.name(), me.color()));
  }

  /** Drops the connection; the server removes the player when its reader sees it close. */
  void close() {
    channel.close();
  }

  /** -1 until the server accepts the join. */
  int myPlayerId() {
    return myPlayerId;
  }

  /**
   * Blocks until the server's level arrives. Nothing on the client can be assembled around a map it
   * does not have yet — the camera, renderer and predictor all take one — so the window is built after
   * this returns.
   */
  TileMap awaitMap(long timeoutMillis) throws InterruptedException {
    if (!mapLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
      throw new IllegalStateException("server did not send the level within " + timeoutMillis + " ms");
    }
    return map;
  }

  Frame latest() {
    return latest;
  }

  /** Sends the current intent; the returned command's {@code seq} is what the predictor remembers. */
  InputCommand sendInput(float moveX, float moveY, float aimAngle, boolean firing, int slot,
      boolean reload) {
    InputCommand cmd = new InputCommand(moveX, moveY, aimAngle, firing, slot, reload, inputSeq++);
    channel.send(cmd);
    return cmd;
  }

  private void handle(Message message) {
    switch (message) {
      case JoinAccepted accepted -> {
        myPlayerId = accepted.playerId();
        // Rebuilt through the same Builder path the server used, so both ends hold the same shape.
        map = TileMap.fromText(String.join("\n", accepted.rows()), accepted.tileSize());
        mapLatch.countDown();
      }
      case Snapshot snapshot -> latest = Frame.of(snapshot);
      default -> {} // Join / InputCommand only travel the other way
    }
  }
}
