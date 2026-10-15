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

  /** Indexed by weapon wire id, which is the {@code WeaponFactory} role, so any family maps. */
  private static final Sound[] SHOT_BY_ROLE = {PISTOL, SHOTGUN, RIFLE, LAUNCH};

  /** The report for a weapon role; a role appended after these sounds like the sidearm. */
  public static Sound shotFor(int weaponId) {
    return weaponId >= 0 && weaponId < SHOT_BY_ROLE.length ? SHOT_BY_ROLE[weaponId] : PISTOL;
  }
}
