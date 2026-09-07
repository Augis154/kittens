package kittens.common.net;

/**
 * Client -&gt; server: the player's intent for one client frame.
 *
 * <p>{@code moveX}/{@code moveY} are a direction in [-1, 1]; {@code aimAngle} is radians;
 * {@code seq} is a monotonically increasing counter so the server can drop stale packets and,
 * later, so the client can reconcile predicted movement.
 */
public record InputCommand(float moveX, float moveY, float aimAngle, boolean firing, long seq)
    implements Message {}
