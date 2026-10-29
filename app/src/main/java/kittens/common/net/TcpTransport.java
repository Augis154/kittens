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
 * {@link Transport} over a TCP socket (Adapter pattern): the socket offers byte streams, so this
 * frames each {@link Message} as one line of JSON and turns end-of-stream and {@code IOException}
 * into a closed transport. Used by both ends.
 */
public final class TcpTransport implements Transport {
  private final Socket socket;
  private final BufferedReader in;
  private final BufferedWriter out;
  private volatile boolean open = true;

  TcpTransport(Socket socket) throws IOException {
    this.socket = socket;
    socket.setTcpNoDelay(true);
    in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
  }

  public static TcpTransport connect(String host, int port) throws IOException {
    return new TcpTransport(new Socket(host, port));
  }

  /** Blocks while the peer's receive window is full, which is what stalls the loop on a slow client. */
  @Override
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

  @Override
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

  @Override
  public void close() {
    open = false;
    try {
      socket.close();
    } catch (IOException ignored) {
      // already closing
    }
  }
}
