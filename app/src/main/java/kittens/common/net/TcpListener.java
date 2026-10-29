package kittens.common.net;

import java.io.IOException;
import java.net.ServerSocket;

/** {@link TransportListener} over a {@code ServerSocket} (Adapter pattern); binds on construction. */
public final class TcpListener implements TransportListener {
  private final ServerSocket socket;

  /** Throws {@code BindException} if the port is taken, e.g. by another running server. */
  public TcpListener(int port) throws IOException {
    socket = new ServerSocket(port);
  }

  @Override
  public Transport accept() throws IOException {
    return new TcpTransport(socket.accept());
  }

  @Override
  public String describe() {
    return "port " + socket.getLocalPort();
  }

  @Override
  public void close() {
    try {
      socket.close();
    } catch (IOException ignored) {
      // already closing
    }
  }
}
