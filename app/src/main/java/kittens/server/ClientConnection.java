package kittens.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import kittens.common.net.Message;
import kittens.common.net.MessageCodec;

/**
 * One connected client: a socket carrying newline-delimited JSON {@link Message}s. Inbound messages
 * are pumped on a dedicated reader thread; {@link #send} may be called from any thread.
 */
final class ClientConnection {
  private final int playerId;
  private final Socket socket;
  private final BufferedReader in;
  private final BufferedWriter out;
  private volatile boolean open = true;

  ClientConnection(int playerId, Socket socket) throws IOException {
    this.playerId = playerId;
    this.socket = socket;
    this.in = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    this.out = new BufferedWriter(
        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
  }

  int playerId() {
    return playerId;
  }

  synchronized void send(Message message) {
    if (!open) {
      return;
    }
    try {
      out.write(MessageCodec.encode(message));
      out.write('\n');
      out.flush();
    } catch (IOException e) {
      close();
    }
  }

  /**
   * Read messages until the socket closes. {@code onMessage} runs per decoded message,
   * {@code onClose} runs exactly once when the connection ends.
   */
  void readLoop(BiConsumer<ClientConnection, Message> onMessage, Consumer<ClientConnection> onClose) {
    try {
      String line;
      while (open && (line = in.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        try {
          onMessage.accept(this, MessageCodec.decode(line));
        } catch (RuntimeException bad) {
          System.err.println("dropping bad message from player " + playerId + ": " + bad);
        }
      }
    } catch (IOException e) {
      // client went away; fall through to cleanup
    } finally {
      close();
      onClose.accept(this);
    }
  }

  void close() {
    open = false;
    try {
      socket.close();
    } catch (IOException ignored) {
      // already closing
    }
  }
}
