package kittens.client.events;

/** Something that reacts to {@link GameEvent}s (Observer pattern). */
@FunctionalInterface
public interface GameObserver {
  void onEvent(GameEvent event);
}
