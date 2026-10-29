package kittens.common.net;

import java.util.List;

/**
 * Server -&gt; client, once per tick. {@code ackSeq} is the last {@link InputCommand#seq()} applied
 * for <em>this</em> recipient; {@code viewerAmmo} and {@code viewerReload} (0 = ready, else
 * progress in (0, 1]) describe the recipient's drawn weapon, {@code viewerMagazines} and
 * {@code viewerReserves} every slot of its arsenal (a reserve of {@code Weapon.UNLIMITED} is
 * bottomless), and {@code viewerBuffs} its own buff timers. {@code wave} and {@code intermission}
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
    List<PlayerInfo> players,
    List<Integer> viewerMagazines,
    List<Integer> viewerReserves,
    BuffState viewerBuffs)
    implements Message {
  public Snapshot {
    entities = List.copyOf(entities);
    players = players == null ? List.of() : List.copyOf(players);
    viewerMagazines = viewerMagazines == null ? List.of() : List.copyOf(viewerMagazines);
    viewerReserves = viewerReserves == null ? List.of() : List.copyOf(viewerReserves);
    viewerBuffs = viewerBuffs == null ? BuffState.NONE : viewerBuffs;
  }
}
