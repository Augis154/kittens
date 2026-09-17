package kittens.common.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * One socket carrying newline-delimited JSON {@link Message}s, used by both ends. {@link #send} may
 * be called from any thread; {@link #readLoop} blocks and belongs on a reader thread.
 */
public final class MessageChannel {
  private final Socket socket;
  private final BufferedReader in;
  private final BufferedWriter out;
  private volatile boolean open = true;

  public MessageChannel(Socket socket) throws IOException {
    this.socket = socket;
    socket.setTcpNoDelay(true);
    in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
  }

  public static MessageChannel connect(String host, int port) throws IOException {
    return new MessageChannel(new Socket(host, port));
  }

  /** Writes and flushes one message; a failed write closes the channel. */
  public synchronized void send(Message message) {
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

  /** Delivers decoded messages until the socket closes; undecodable lines are logged and skipped. */
  public void readLoop(Consumer<Message> onMessage) {
    try {
      String line;
      while (open && (line = in.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        Message message;
        try {
          message = MessageCodec.decode(line);
        } catch (RuntimeException bad) {
          System.err.println("dropping bad message: " + bad);
          continue;
        }
        onMessage.accept(message);
      }
    } catch (IOException e) {
      // peer went away
    } finally {
      close();
    }
  }

  public void close() {
    open = false;
    try {
      socket.close();
    } catch (IOException ignored) {
      // already closing
    }
  }
}
