package kittens.common.net;

import java.util.List;

/**
 * Server -&gt; client, once per tick. {@code ackSeq} is the last {@link InputCommand#seq()} applied
 * for <em>this</em> recipient; {@code viewerAmmo} and {@code viewerReload} (0 = ready, else
 * progress in (0, 1]) describe the recipient's own weapon.
 */
public record Snapshot(
    long tick,
    long ackSeq,
    List<EntityState> entities,
    int viewerAmmo,
    float viewerReload)
    implements Message {
  public Snapshot {
    entities = List.copyOf(entities);
  }
}
