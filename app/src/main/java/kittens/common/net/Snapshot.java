package kittens.common.net;

import java.util.List;

/**
 * Server -&gt; client: the authoritative world at a given tick. Sent at a fixed rate (~20 Hz); the
 * client interpolates between the last two it received.
 */
public record Snapshot(long tick, List<EntityState> entities) implements Message {
  public Snapshot {
    entities = List.copyOf(entities);
  }
}
