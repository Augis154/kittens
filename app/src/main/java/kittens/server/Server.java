package kittens.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kittens.common.GameConfig;
import kittens.common.map.LevelGenerator;
import kittens.common.map.TileMap;
import kittens.common.net.InputCommand;
import kittens.common.net.Join;
import kittens.common.net.JoinAccepted;
import kittens.common.net.Message;
import kittens.common.net.MessageChannel;
import kittens.common.net.PlayerInfo;
import kittens.common.net.ProfileUpdate;
import kittens.common.weapon.WeaponFactory;

/**
 * Accepts sockets on the main thread, reads each client on its own {@code client-<id>} thread, and
 * runs the authoritative {@link GameWorld} on the {@link ServerLoop} thread, which also sends one
 * {@code Snapshot} per client per tick (the ack and ammo fields are recipient-specific).
 *
 * Singleton ERIKAS
 */
public final class Server {
  private static final Server INSTANCE = new Server();

  private final GameWorld world;
  /** Built once, so every client sees the same arena; its glyph rows are what goes on the wire. */
  private final TileMap level;
  private final Map<Integer, MessageChannel> clients = new ConcurrentHashMap<>();
  private int nextPlayerId;

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
    Server.getInstance().run();
  }

  private void run() throws IOException {
    new ServerLoop(GameConfig.TICK_HZ, this::tick).start();
    try (ServerSocket serverSocket = new ServerSocket(GameConfig.PORT)) {
      System.out.println("Server on port " + GameConfig.PORT + ", map " + GameConfig.MAP_ID + " ("
          + level.width() + "x" + level.height() + ", " + level.spawnPoints().size() + " entrances) @ "
          + GameConfig.TICK_HZ + " Hz");
      while (true) {
        MessageChannel channel = new MessageChannel(serverSocket.accept());
        int id = nextPlayerId++;
        clients.put(id, channel);
        Thread reader = new Thread(() -> serve(id, channel), "client-" + id);
        reader.setDaemon(true);
        reader.start();
      }
    }
  }

  private void serve(int id, MessageChannel channel) {
    channel.readLoop(message -> onMessage(id, channel, message));
    clients.remove(id);
    world.removePlayer(id);
    System.out.println("player " + id + " left");
  }

  private void onMessage(int id, MessageChannel channel, Message message) {
    switch (message) {
      case Join join -> {
        WeaponFactory family = WeaponFactory.named(join.arsenal());
        PlayerInfo who = world.addPlayer(id, family, join.clientName(), join.color());
        channel.send(new JoinAccepted(id, GameConfig.MAP_ID, level.tileSize(), level.rows()));
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
    clients.forEach((id, channel) -> channel.send(world.snapshotFor(id)));
  }
}
