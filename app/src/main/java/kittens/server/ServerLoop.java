package kittens.server;

import java.util.function.DoubleConsumer;

/**
 * Fixed-timestep Game Loop on a daemon thread: real time is accumulated and drained in whole
 * {@code dt} steps, so the simulation advances at a constant rate regardless of jitter.
 */
final class ServerLoop {
  private final double dt;
  private final DoubleConsumer step;

  ServerLoop(int hz, DoubleConsumer step) {
    this.dt = 1.0 / hz;
    this.step = step;
  }

  void start() {
    Thread thread = new Thread(this::run, "server-loop");
    thread.setDaemon(true);
    thread.start();
  }

  private void run() {
    long previous = System.nanoTime();
    double accumulator = 0;
    while (true) {
      long now = System.nanoTime();
      accumulator = Math.min(0.25, accumulator + (now - previous) / 1e9); // no catch-up spiral
      previous = now;
      while (accumulator >= dt) {
        step.accept(dt);
        accumulator -= dt;
      }
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        return;
      }
    }
  }
}
