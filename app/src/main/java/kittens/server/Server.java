package kittens.server;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import kittens.common.GameConfig;
import kittens.common.map.LevelGenerator;
import kittens.common.map.TileMap;
import kittens.common.net.InputCommand;
import kittens.common.net.Join;
import kittens.common.net.JoinAccepted;
import kittens.common.net.LoopbackListener;
import kittens.common.net.Message;
import kittens.common.net.PlayerInfo;
import kittens.common.net.ProfileUpdate;
import kittens.common.net.TcpListener;
import kittens.common.net.Transport;
import kittens.common.net.TransportListener;
import kittens.common.weapon.WeaponFactory;

/**
 * Accepts clients from a {@link TransportListener}, reads each on its own {@code client-<id>}
 * thread, and runs the authoritative {@link GameWorld} on the {@link ServerLoop} thread, which also
 * sends one {@code Snapshot} per client per tick (the ack and ammo fields are recipient-specific).
 * It never sees a socket: {@link #main} listens on TCP, and {@link #loopback} serves clients in the
 * same process, which is how the client plays offline.
 *
 * Singleton ERIKAS
 */
public final class Server {
  private static final Server INSTANCE = new Server();

  private final GameWorld world;
  /** Built once, so every client sees the same arena; its glyph rows are what goes on the wire. */
  private final TileMap level;
  private final Map<Integer, Transport> clients = new ConcurrentHashMap<>();
  /** Atomic because each listener accepts on its own thread. */
  private final AtomicInteger nextPlayerId = new AtomicInteger();
  private final AtomicBoolean loopStarted = new AtomicBoolean();
  private LoopbackListener loopback; // guarded by this

  private Server() {
    this.level = buildLevel();
    this.world = new GameWorld(level);
  }

  /**
   * The level is assembled here, on the server, and shipped to clients — the only place a level is
   * ever chosen. Seeded so a run is reproducible; {@code ThreadLocalRandom} would make the same seed
   * produce a different arena on each start.
   */
  private static TileMap buildLevel() {
    if (!GameConfig.PROCEDURAL_LEVEL) {
      return TileMap.fromResource(GameConfig.MAP_RESOURCE, GameConfig.TILE);
    }
    long seed = GameConfig.LEVEL_SEED == 0L ? System.nanoTime() : GameConfig.LEVEL_SEED;
    System.out.println("generating level from seed " + seed);
    return LevelGenerator.generate(seed, GameConfig.TILE);
  }

  public static Server getInstance() {
    return INSTANCE;
  }

  public static void main(String[] args) throws IOException {
    Server.getInstance().listen(new TcpListener(GameConfig.PORT));
  }

  /**
   * The in-process way in, for offline play and probes: started on first use, accepting on a
   * {@code server-accept} daemon thread, and never bound to a port, so it works while another server
   * holds {@link GameConfig#PORT}. Clients join with {@link LoopbackListener#connect}.
   */
  public synchronized LoopbackListener loopback() {
    if (loopback == null) {
      LoopbackListener listener = new LoopbackListener();
      Thread acceptor = new Thread(() -> {
        try {
          listen(listener);
        } catch (IOException closed) {
          // the listener was closed; nothing more will join through it
        }
      }, "server-accept");
      acceptor.setDaemon(true);
      acceptor.start();
      loopback = listener;
    }
    return loopback;
  }

  /**
   * Accepts clients until the listener closes; blocks. Several listeners may feed the one world, and
   * the simulation starts with the first.
   */
  private void listen(TransportListener listener) throws IOException {
    if (loopStarted.compareAndSet(false, true)) {
      new ServerLoop(GameConfig.TICK_HZ, this::tick).start();
    }
    try (listener) {
      System.out.println("Server on " + listener.describe() + ", map " + GameConfig.MAP_ID + " ("
          + level.width() + "x" + level.height() + ", " + level.spawnPoints().size() + " entrances) @ "
          + GameConfig.TICK_HZ + " Hz");
      while (true) {
        Transport transport = listener.accept();
        int id = nextPlayerId.getAndIncrement();
        clients.put(id, transport);
        Thread reader = new Thread(() -> serve(id, transport), "client-" + id);
        reader.setDaemon(true);
        reader.start();
      }
    }
  }

  private void serve(int id, Transport transport) {
    transport.readLoop(message -> onMessage(id, transport, message));
    clients.remove(id);
    world.removePlayer(id);
    System.out.println("player " + id + " left");
  }

  private void onMessage(int id, Transport transport, Message message) {
    switch (message) {
      case Join join -> {
        WeaponFactory family = WeaponFactory.named(join.arsenal());
        PlayerInfo who = world.addPlayer(id, family, join.clientName(), join.color());
        transport.send(new JoinAccepted(id, GameConfig.MAP_ID, level.tileSize(), level.rows()));
        System.out.println("player " + id + " \"" + who.name() + "\" joined (" + who.color()
            + ", " + family.name() + ")");
      }
      case ProfileUpdate update -> {
        PlayerInfo who = world.updateProfile(id, update.name(), update.color());
        if (who != null) {
          System.out.println("player " + id + " is now \"" + who.name() + "\" (" + who.color() + ")");
        }
      }
      case InputCommand input -> world.applyInput(id, input);
      default -> System.err.println("unexpected message from player " + id + ": " + message);
    }
  }

  private void tick(double dt) {
    world.tick(dt);
    clients.forEach((id, transport) -> transport.send(world.snapshotFor(id)));
  }
}
