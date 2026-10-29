package kittens.common.net;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * {@link Transport} between two ends in one process (Adapter pattern): adapts a pair of
 * {@link BlockingQueue}s, one per direction, so a client and server can talk with no port at all.
 * Ends come in pairs from {@link LoopbackListener#connect}.
 *
 * <p>It deliberately behaves like the wire rather than like a method call. Messages are still
 * encoded to JSON, so anything that would not survive serialization fails here too. Each queue is
 * bounded and {@link #send} blocks when it is full, as a socket does when the peer's window fills.
 * And the two ends share one closed flag, so closing either end closes the link for both.
 */
public final class LoopbackTransport implements Transport {
  /** About two seconds of snapshots: the stand-in for a socket's buffers. */
  private static final int CAPACITY = 64;
  /**
   * Queues have no end-of-stream, so blocked reads and writes wake this often to notice a close.
   * Messages themselves never wait on it: a queue operation returns the moment it can proceed.
   */
  private static final long CLOSE_POLL_MS = 50;

  private final BlockingQueue<String> in;
  private final BlockingQueue<String> out;
  private final AtomicBoolean closed;

  private LoopbackTransport(BlockingQueue<String> in, BlockingQueue<String> out,
      AtomicBoolean closed) {
    this.in = in;
    this.out = out;
    this.closed = closed;
  }

  /** The two ends of one fresh link. */
  record Link(LoopbackTransport client, LoopbackTransport server) {}

  static Link link() {
    BlockingQueue<String> toServer = new ArrayBlockingQueue<>(CAPACITY);
    BlockingQueue<String> toClient = new ArrayBlockingQueue<>(CAPACITY);
    AtomicBoolean closed = new AtomicBoolean();
    return new Link(new LoopbackTransport(toClient, toServer, closed),
        new LoopbackTransport(toServer, toClient, closed));
  }

  @Override
  public void send(Message message) {
    String line = MessageCodec.encode(message);
    try {
      while (!closed.get()) {
        if (out.offer(line, CLOSE_POLL_MS, TimeUnit.MILLISECONDS)) {
          return;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      close();
    }
  }

  /** Once closed, still drains what was already queued, as a socket reader does before end-of-stream. */
  @Override
  public void readLoop(Consumer<Message> onMessage) {
    try {
      while (true) {
        String line = in.poll(CLOSE_POLL_MS, TimeUnit.MILLISECONDS);
        if (line == null) {
          if (closed.get()) {
            return;
          }
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
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      close();
    }
  }

  @Override
  public void close() {
    closed.set(true);
  }
}
