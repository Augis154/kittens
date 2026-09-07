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
import kittens.common.net.EntityState;
import kittens.common.net.InputCommand;
import kittens.common.net.Join;
import kittens.common.net.JoinAccepted;
import kittens.common.net.Message;
import kittens.common.net.MessageCodec;
import kittens.common.net.Snapshot;

/**
 * The client's network half: connects, sends {@link Join} then a stream of {@link InputCommand}s,
 * and exposes the latest {@link Snapshot} (entities + input ack) for the renderer and predictor.
 */
final class GameClient {
  private final BufferedReader in;
  private final BufferedWriter out;
  private final AtomicLong inputSeq = new AtomicLong();

  private volatile int myPlayerId = -1;
  private final Map<Integer, EntityState> entities = new ConcurrentHashMap<>();
  private volatile long ackSeq = -1;
  private final AtomicLong snapshotVersion = new AtomicLong();

  GameClient(String host, int port) throws IOException {
    Socket socket = new Socket(host, port);
    socket.setTcpNoDelay(true);
    in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
  }

  void start() {
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

  /** Seq of the last input the server has confirmed applying for us. */
  long ackSeq() {
    return ackSeq;
  }

  /** Bumps on every snapshot; the predictor reconciles when it changes. */
  long snapshotVersion() {
    return snapshotVersion.get();
  }

  /** Send the current intent and return the command (its {@code seq} is needed for replay). */
  InputCommand sendInput(float moveX, float moveY, float aimAngle, boolean firing, int weaponId) {
    InputCommand cmd = new InputCommand(
        moveX, moveY, aimAngle, firing, weaponId, inputSeq.getAndIncrement());
    send(cmd);
    return cmd;
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
        if (!line.isBlank()) {
          handle(MessageCodec.decode(line));
        }
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
        ackSeq = snapshot.ackSeq();
        snapshotVersion.incrementAndGet();
      }
      default -> {
        // Join / InputCommand are client -> server only.
      }
    }
  }
}
