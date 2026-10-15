package kittens.common.net;

import java.util.List;

/**
 * Server -&gt; client, once per tick. {@code ackSeq} is the last {@link InputCommand#seq()} applied
 * for <em>this</em> recipient; {@code viewerAmmo} and {@code viewerReload} (0 = ready, else
 * progress in (0, 1]) describe the recipient's own weapon. {@code wave} and {@code intermission}
 * (seconds to the next wave, 0 while one runs) are the same for everyone. {@code players} is the
 * roster, sent whole every tick rather than on change: it is a few bytes per player, a late joiner
 * needs no catch-up, and a rename shows up on the very next tick.
 */
public record Snapshot(
    long tick,
    long ackSeq,
    List<EntityState> entities,
    int viewerAmmo,
    float viewerReload,
    int wave,
    float intermission,
    List<PlayerInfo> players)
    implements Message {
  public Snapshot {
    entities = List.copyOf(entities);
    players = players == null ? List.of() : List.copyOf(players);
  }
}
