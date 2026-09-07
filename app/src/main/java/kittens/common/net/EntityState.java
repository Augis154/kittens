package kittens.common.net;

/**
 * One entity as seen on the wire: a flat, render-ready snapshot row. Not a {@link Message} on its
 * own — it only ever travels inside a {@link Snapshot}.
 *
 * <p>{@code kind} is a short tag the client maps to a sprite ("cat", "rat", "mouse", "bullet").
 */
public record EntityState(
    int id, String kind, float x, float y, float angle, float hp, int weaponId) {}
