package kittens.common.net;

/** Everything that can travel between client and server; {@link MessageCodec} does the JSON. */
public sealed interface Message permits Join, JoinAccepted, InputCommand, Snapshot {}
