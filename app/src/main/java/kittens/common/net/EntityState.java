package kittens.common.net;

/**
 * One entity as seen on the wire: a flat, render-ready snapshot row.
 *
 * <p>Two kinds overload fields instead of widening the record: for {@link EntityKind#BOOM},
 * {@code angle} is the current radius and {@code hp} the final radius; for pickups, {@code hp} is
 * the seconds of life left. A {@link EntityKind#CRATE}'s {@code hp} is its real health.
 * {@code invulnerableFor} is a player's remaining damage immunity and {@code buffs} its {@link Buff}
 * mask (0 for every other kind), so teammates can see who is protected or powered up. {@code weaponId} is unique across weapon families (see
 * {@code WeaponFactory.decode}), since players may have chosen different ones.
 */
public record EntityState(
    int id,
    EntityKind kind,
    float x,
    float y,
    float angle,
    float hp,
    int weaponId,
    float invulnerableFor,
    int buffs) {}
