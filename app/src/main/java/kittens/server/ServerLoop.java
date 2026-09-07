package kittens.server;

import java.util.function.DoubleConsumer;

/**
 * A fixed-timestep loop on its own daemon thread. Real elapsed time is accumulated and drained in
 * whole {@code dt} steps, so the simulation advances at a constant rate regardless of jitter.
 */
final class ServerLoop {
  private final double dt;
  private final DoubleConsumer step;
  private volatile boolean running;

  ServerLoop(int hz, DoubleConsumer step) {
    this.dt = 1.0 / hz;
    this.step = step;
  }

  void start() {
    running = true;
    Thread thread = new Thread(this::run, "server-loop");
    thread.setDaemon(true);
    thread.start();
  }

  void stop() {
    running = false;
  }

  private void run() {
    long previous = System.nanoTime();
    double accumulator = 0;
    while (running) {
      long now = System.nanoTime();
      accumulator += (now - previous) / 1_000_000_000.0;
      previous = now;

      // Guard against a long stall turning into a spiral of catch-up steps.
      if (accumulator > 0.25) {
        accumulator = 0.25;
      }
      while (accumulator >= dt) {
        step.accept(dt);
        accumulator -= dt;
      }

      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        running = false;
      }
    }
  }
}
