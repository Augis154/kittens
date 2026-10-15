package kittens.common.net;

/**
 * Client -&gt; server: request to join the running match, carrying the {@code WeaponFactory#name()}
 * of the arsenal the player chose (an unrecognised or missing one falls back to the first family),
 * and the player's name and kitten colour ("auto" lets the server pick by join order).
 */
public record Join(String clientName, String arsenal, String color) implements Message {}
