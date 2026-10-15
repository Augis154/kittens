package kittens.common.net;

/**
 * One row of a snapshot's roster: who a player is, as opposed to the {@link EntityState} saying
 * where they are. {@code color} is always a valid kitten sprite name; the server resolves "auto" and
 * anything unknown before it gets here.
 */
public record PlayerInfo(int id, String name, String color) {}
