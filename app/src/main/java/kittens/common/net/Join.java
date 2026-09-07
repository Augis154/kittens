package kittens.common.net;

/** Client -&gt; server: request to join the running match. */
public record Join(String clientName) implements Message {}
