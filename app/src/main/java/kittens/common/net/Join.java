package kittens.common.net;

/**
 * Client -&gt; server: request to join the running match, carrying the {@code WeaponFactory#name()}
 * of the arsenal the player chose. An unrecognised or missing name falls back to the first family.
 */
public record Join(String clientName, String arsenal) implements Message {}
