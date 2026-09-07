package kittens.common.net;

/** Server -&gt; client: the join succeeded; here is your player id and the map to load. */
public record JoinAccepted(int playerId, String mapId) implements Message {}
