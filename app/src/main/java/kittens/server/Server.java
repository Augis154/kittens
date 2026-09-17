package kittens.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.net.InputCommand;
import kittens.common.net.Join;
import kittens.common.net.JoinAccepted;
import kittens.common.net.Message;
import kittens.common.net.MessageChannel;

/**
 * Accepts sockets on the main thread, reads each client on its own {@code client-<id>} thread, and
 * runs the authoritative {@link GameWorld} on the {@link ServerLoop} thread, which also sends one
 * {@code Snapshot} per client per tick (the ack and ammo fields are recipient-specific).
 */
public final class Server {
  private final GameWorld world =
      new GameWorld(TileMap.fromResource(GameConfig.MAP_RESOURCE, GameConfig.TILE));
  private final Map<Integer, MessageChannel> clients = new ConcurrentHashMap<>();
  private int nextPlayerId;

  public static void main(String[] args) throws IOException {
    new Server().run();
  }

  private void run() throws IOException {
    new ServerLoop(GameConfig.TICK_HZ, this::tick).start();
    try (ServerSocket serverSocket = new ServerSocket(GameConfig.PORT)) {
      System.out.println("Server on port " + GameConfig.PORT + ", map " + GameConfig.MAP_ID
          + " @ " + GameConfig.TICK_HZ + " Hz");
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
      case Join ignored -> {
        world.addPlayer(id);
        channel.send(new JoinAccepted(id, GameConfig.MAP_ID));
        System.out.println("player " + id + " joined (" + GameConfig.kittenSprite(id) + ")");
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
