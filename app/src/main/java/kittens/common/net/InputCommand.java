package kittens.common.net;

/**
 * Client -&gt; server: the player's intent for one input tick.
 *
 * <p>{@code moveX}/{@code moveY} are a direction in [-1, 1]; {@code aimAngle} is radians;
 * {@code weaponId} is the currently selected {@link kittens.common.weapon.Weapon}; {@code reload}
 * is a one-shot request to reload the current magazine early; {@code seq} is a monotonically
 * increasing counter the server acks so the client can reconcile its prediction.
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
