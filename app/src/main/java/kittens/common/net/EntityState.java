package kittens.common.net;

/**
 * One entity as seen on the wire: a flat, render-ready snapshot row.
 *
 * <p>Two kinds overload fields instead of widening the record: for {@link EntityKind#BOOM},
 * {@code angle} is the current radius and {@code hp} the final radius; for pickups, {@code hp} is
 * the seconds of life left. {@code invulnerableFor} is a player's remaining damage immunity, so
 * teammates can see who is protected.
 */
public record EntityState(
    int id,
    EntityKind kind,
    float x,
    float y,
    float angle,
    float hp,
    int weaponId,
    float invulnerableFor) {}
