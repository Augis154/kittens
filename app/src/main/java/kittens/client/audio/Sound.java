package kittens.client.audio;

/** Every effect the game can play. Each is synthesized once by {@link SoundBank}; none ship as files. */
public enum Sound {
  PISTOL,
  SHOTGUN,
  RIFLE,
  LAUNCH,
  BOOM,
  HIT,
  SQUEAK,
  HURT,
  HEAL,
  AMMO,
  RELOAD,
  SWITCH;

  /** Indexed by role slot, which every {@code WeaponFactory} family shares, so any family maps. */
  private static final Sound[] SHOT_BY_ROLE = {PISTOL, SHOTGUN, RIFLE, LAUNCH};

  /** The report for a weapon slot; a slot appended after these sounds like the sidearm. */
  public static Sound shotFor(int slot) {
    return slot >= 0 && slot < SHOT_BY_ROLE.length ? SHOT_BY_ROLE[slot] : PISTOL;
  }
}
