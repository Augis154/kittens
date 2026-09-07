package kittens.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kittens.common.GameConfig;
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.net.Join;
import kittens.common.net.JoinAccepted;
import kittens.common.net.Message;
import kittens.common.net.MessageCodec;
import kittens.common.net.Snapshot;

/**
 * The client's network half: connects, sends {@link Join} then a stream of {@link InputCommand}s,
 * and keeps the most recent {@link Snapshot}'s entities in a concurrent map the renderer reads.
 */
final class GameClient {
  private final BufferedReader in;
  private final BufferedWriter out;
  private final AtomicLong inputSeq = new AtomicLong();

  private volatile int myPlayerId = -1;
  private final Map<Integer, EntityState> entities = new ConcurrentHashMap<>();

  GameClient(String host, int port) throws IOException {
    Socket socket = new Socket(host, port);
    socket.setTcpNoDelay(true);
    in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
  }

  void start() throws IOException {
    send(new Join("kitten"));
    Thread reader = new Thread(this::readLoop, "client-net");
    reader.setDaemon(true);
    reader.start();
  }

  int myPlayerId() {
    return myPlayerId;
  }

  Map<Integer, EntityState> entities() {
    return entities;
  }

  /** Send the current movement intent. {@code moveX}/{@code moveY} are each in [-1, 1]. */
  void sendInput(float moveX, float moveY, float aimAngle, boolean firing) {
    send(new InputCommand(moveX, moveY, aimAngle, firing, inputSeq.getAndIncrement()));
  }

  private synchronized void send(Message message) {
    try {
      out.write(MessageCodec.encode(message));
      out.write('\n');
      out.flush();
    } catch (IOException e) {
      System.err.println("send failed: " + e);
    }
  }

  private void readLoop() {
    try {
      String line;
      while ((line = in.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        handle(MessageCodec.decode(line));
      }
    } catch (IOException e) {
      System.err.println("connection closed: " + e);
    }
  }

  private void handle(Message message) {
    switch (message) {
      case JoinAccepted accepted -> myPlayerId = accepted.playerId();
      case Snapshot snapshot -> {
        entities.keySet().retainAll(
            snapshot.entities().stream().map(EntityState::id).toList());
        for (EntityState e : snapshot.entities()) {
          entities.put(e.id(), e);
        }
      }
      default -> {
        // Join / InputCommand are client -> server only; ignore if echoed.
      }
    }
  }
}
