package kittens.common.net;

/**
 * A timed or one-shot advantage a kitten can carry, granted by a pickup. Every kitten's buffs ride
 * on its {@link EntityState#buffs()} as a bit mask, so teammates can see who is shielded or
 * powered up; the viewer's own timers come separately in {@link BuffState}.
 */
public enum Buff {
  DOUBLE_DAMAGE,
  SHIELD,
  /** Catnip frenzy: the arsenal's clock runs at double speed, so fire rate and reloads both double. */
  CATNIP,
  /** The next lethal hit is survived instead. */
  NINE_LIVES;

  public int bit() {
    return 1 << ordinal();
  }

  public boolean in(int mask) {
    return (mask & bit()) != 0;
  }
}
