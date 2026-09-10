package kittens.common.net;

/**
 * One entity as seen on the wire: a flat, render-ready snapshot row. Not a {@link Message} on its
 * own — it only ever travels inside a {@link Snapshot}.
 *
 * <p>{@code kind} is a short tag the client maps to a sprite ("cat", "rat", "mouse", "bullet").
 *
 * <p>{@code invulnerableFor} is the seconds of damage immunity a player has left — from spawn grace
 * or from the i-frame every hit grants — and 0 for everything else. It rides on every entity rather
 * than in {@link Snapshot}'s viewer-only fields so that teammates can see who is currently
 * protected, not just the player who owns the immunity.
 */
public record EntityState(
    int id,
    String kind,
    float x,
    float y,
    float angle,
    float hp,
    int weaponId,
    float invulnerableFor) {}
