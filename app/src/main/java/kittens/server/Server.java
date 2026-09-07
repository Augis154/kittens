package kittens.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import kittens.common.GameConfig;
import kittens.common.map.TileMap;
import kittens.common.net.InputCommand;
import kittens.common.net.Join;
import kittens.common.net.JoinAccepted;
import kittens.common.net.Message;
import kittens.common.net.Snapshot;

/**
 * Accepts client sockets, runs the authoritative {@link GameWorld} on a fixed tick, and broadcasts a
 * {@link Snapshot} to every client each tick. Wire format is newline-delimited JSON.
 */
public final class Server {
  private final GameWorld world =
      new GameWorld(TileMap.fromResource(GameConfig.MAP_RESOURCE, GameConfig.TILE));
  private final CopyOnWriteArrayList<ClientConnection> connections = new CopyOnWriteArrayList<>();
  private final AtomicInteger nextPlayerId = new AtomicInteger();

  public static void main(String[] args) throws IOException {
    new Server().run();
  }

  private void run() throws IOException {
    new ServerLoop(GameConfig.TICK_HZ, this::tick).start();

    try (ServerSocket serverSocket = new ServerSocket(GameConfig.PORT)) {
      System.out.println("Server on port " + GameConfig.PORT + ", map " + world.mapId()
          + " @ " + GameConfig.TICK_HZ + " Hz");
      while (true) {
        Socket socket = serverSocket.accept();
        socket.setTcpNoDelay(true);
        int id = nextPlayerId.getAndIncrement();
        ClientConnection conn = new ClientConnection(id, socket);
        connections.add(conn);
        Thread reader = new Thread(
            () -> conn.readLoop(this::onMessage, this::onDisconnect), "client-" + id);
        reader.setDaemon(true);
        reader.start();
      }
    }
  }

  private void onMessage(ClientConnection conn, Message message) {
    switch (message) {
      case Join ignored -> {
        world.addPlayer(conn.playerId());
        conn.send(new JoinAccepted(conn.playerId(), world.mapId()));
        System.out.println("player " + conn.playerId() + " joined ("
            + GameConfig.kittenSprite(conn.playerId()) + ")");
      }
      case InputCommand input -> world.applyInput(conn.playerId(), input);
      default -> System.err.println("unexpected message from client: " + message);
    }
  }

  private void onDisconnect(ClientConnection conn) {
    connections.remove(conn);
    world.removePlayer(conn.playerId());
    System.out.println("player " + conn.playerId() + " left");
  }

  private void tick(double dt) {
    world.tick(dt);
    Snapshot snapshot = world.snapshot();
    for (ClientConnection conn : connections) {
      conn.send(snapshot);
    }
  }
}
