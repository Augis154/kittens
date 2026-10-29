package kittens.common.net;

/**
 * The recipient's own buffs in detail, for the HUD: seconds left on the timed ones, the shield's
 * remaining points and seconds, and whether a spare life is banked. Everyone else's buffs only
 * travel as the {@link EntityState#buffs()} mask.
 */
public record BuffState(float doubleDamage, float catnip, float shield, float shieldTime,
    boolean nineLives) {
  public static final BuffState NONE = new BuffState(0f, 0f, 0f, 0f, false);
}
