package kittens.common.net;

/**
 * Client -&gt; server: one tick of intent (a Command object). {@code moveX}/{@code moveY} are in
 * [-1, 1], {@code reload} is a one-shot request, and {@code seq} is acked back in {@link Snapshot}
 * so the client can reconcile its prediction. {@code slot} is a position in the sender's own
 * arsenal, not a wire weapon id — the server resolves it against that player's family, so a client
 * can never select a weapon it does not carry.
 */
public record InputCommand(
    float moveX,
    float moveY,
    float aimAngle,
    boolean firing,
    int slot,
    boolean reload,
    long seq)
    implements Message {}
