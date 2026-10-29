package kittens.common.net;

import java.io.IOException;

/**
 * The server's way in: hands over one {@link Transport} per client that connects. The second
 * Adapter Target beside {@link Transport}, because a socket has two roles — {@link TcpListener}
 * adapts a {@code ServerSocket}, {@link LoopbackListener} a queue of in-process connections.
 */
public interface TransportListener extends AutoCloseable {
  /** Blocks until a client connects; throws once the listener is closed. */
  Transport accept() throws IOException;

  /** Where clients reach this listener, for the server's log. */
  String describe();

  /** Idempotent; a blocked {@link #accept} then throws. */
  @Override
  void close();
}
