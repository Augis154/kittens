package kittens.common.net;

/**
 * Client -&gt; server: the player renamed themselves or picked another colour mid-match. Same rules as
 * the name and colour in {@link Join}, and the server applies them the same way.
 */
public record ProfileUpdate(String name, String color) implements Message {}
