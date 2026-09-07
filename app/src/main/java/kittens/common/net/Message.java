package kittens.common.net;

/**
 * Marker for every value that can travel over the wire between client and server. The prototype
 * protocol is deliberately tiny; {@link MessageCodec} turns these to and from JSON.
 */
public sealed interface Message permits Join, JoinAccepted, InputCommand, Snapshot {}
