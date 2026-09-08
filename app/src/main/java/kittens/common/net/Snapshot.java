package kittens.common.net;

import java.util.List;

/**
 * Server -&gt; client: the authoritative world at a given tick, sent once per tick (~30 Hz).
 *
 * <p>{@code ackSeq} is the {@link InputCommand#seq()} of the last input the server has applied for
 * <em>this</em> recipient — the client drops acknowledged inputs and replays the rest on top of the
 * authoritative state (client-side prediction reconciliation).
 *
 * <p>{@code viewerAmmo} is the recipient's rounds left in the current weapon's magazine;
 * {@code viewerReload} is 0 when ready to fire, otherwise reload progress in (0, 1].
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
