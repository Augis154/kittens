package kittens.common.net;

/**
 * Client -&gt; server: one tick of intent (a Command object). {@code moveX}/{@code moveY} are in
 * [-1, 1], {@code reload} is a one-shot request, and {@code seq} is acked back in {@link Snapshot}
 * so the client can reconcile its prediction.
 */
public record InputCommand(
    float moveX,
    float moveY,
    float aimAngle,
    boolean firing,
    int weaponId,
    boolean reload,
    long seq)
    implements Message {}
