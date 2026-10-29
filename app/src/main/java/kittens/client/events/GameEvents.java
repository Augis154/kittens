package kittens.client.events;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The subject (Observer pattern): whoever detects what happened publishes it here, and everything
 * that cares — sound, visual effects, whatever comes next — subscribes, so the detector never has
 * to know who is listening.
 *
 * <p>Delivery is synchronous, in subscription order, on the publishing thread, which in the client
 * is the Swing EDT. The list is copy-on-write so an observer may unsubscribe itself from inside
 * {@code onEvent}.
 */
public final class GameEvents {
  private final CopyOnWriteArrayList<GameObserver> observers = new CopyOnWriteArrayList<>();

  /** Subscribing the same observer twice is a no-op, so it is never notified twice per event. */
  public void subscribe(GameObserver observer) {
    observers.addIfAbsent(Objects.requireNonNull(observer, "observer must not be null"));
  }

  public void unsubscribe(GameObserver observer) {
    observers.remove(observer);
  }

  public void publish(GameEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    for (GameObserver observer : observers) {
      try {
        observer.onEvent(event);
      } catch (RuntimeException e) {
        // One broken observer must not stop the others, or the frame loop that published.
        System.err.println("observer " + observer + " failed on " + event + ": " + e);
      }
    }
  }
}
