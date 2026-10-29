package kittens.common.net;

import java.util.function.Consumer;

/**
 * One end of a connection carrying {@link Message}s, whatever actually moves them. This is the
 * Target of the Adapter pattern: {@link TcpTransport} adapts a {@code Socket}'s byte streams and
 * {@link LoopbackTransport} a pair of in-memory queues, and the client and server are written
 * against this interface alone, so they run unchanged over either.
 */
public interface Transport {
  /** Sends one message; any thread. A failed send closes the transport rather than throwing. */
  void send(Message message);

  /**
   * Delivers decoded messages until the connection closes, then returns. Blocks, so it belongs on a
   * reader thread. Undecodable messages are logged and skipped.
   */
  void readLoop(Consumer<Message> onMessage);

  /** Idempotent. The peer's {@link #readLoop} returns once it has read what was already sent. */
  void close();
}
