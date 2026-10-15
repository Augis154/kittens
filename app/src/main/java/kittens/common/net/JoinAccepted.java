package kittens.common.net;

import java.util.List;

/**
 * Server -&gt; client: the join succeeded; here is your player id and the level to load.
 *
 * <p>The level travels as glyph rows, not as a seed, so the client never runs the generator and never
 * has to agree on one — the server's build is the single source of the map for both ends. {@code
 * tileSize} travels with it because the client's camera and sprite sizing depend on it.
 */
public record JoinAccepted(int playerId, String mapId, float tileSize, List<String> rows)
    implements Message {}
