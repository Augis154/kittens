package kittens.common.net;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * {@link TransportListener} for clients in the same process (Adapter pattern): adapts a queue of
 * pending {@link LoopbackTransport} links. {@link #connect} is the in-process counterpart of
 * {@link TcpTransport#connect}, and nothing here ever opens a port.
 */
public final class LoopbackListener implements TransportListener {
  /** As in {@link LoopbackTransport}: a queue cannot be closed, so a waiting accept checks this often. */
  private static final long CLOSE_POLL_MS = 50;

  private final BlockingQueue<LoopbackTransport> pending = new LinkedBlockingQueue<>();
  private volatile boolean closed;

  /** Any thread. Queues the server's end for {@link #accept} and returns the client's. */
  public Transport connect() throws IOException {
    if (closed) {
      throw new IOException("loopback listener is closed");
    }
    LoopbackTransport.Link link = LoopbackTransport.link();
    pending.add(link.server());
    return link.client();
  }

  @Override
  public Transport accept() throws IOException {
    try {
      while (true) {
        LoopbackTransport next = pending.poll(CLOSE_POLL_MS, TimeUnit.MILLISECONDS);
        if (next != null) {
          return next;
        }
        if (closed) {
          throw new IOException("loopback listener is closed");
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterruptedIOException("interrupted waiting for a loopback client");
    }
  }

  @Override
  public String describe() {
    return "in-process loopback";
  }

  /** Links nobody will now accept are closed, so their clients see the connection drop. */
  @Override
  public void close() {
    closed = true;
    for (LoopbackTransport orphan; (orphan = pending.poll()) != null; ) {
      orphan.close();
    }
  }
}
