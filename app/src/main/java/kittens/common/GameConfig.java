package kittens.common;

/** Constants shared by the client and server so the two simulations agree. */
public final class GameConfig {
  private GameConfig() {}

  public static final int PORT = 7000;

  /** The map every client loads for this prototype. */
  public static final String MAP_ID = "arena";

  public static final String MAP_RESOURCE = "maps/arena.txt";

  /** Pixel size of one map tile; also the size the kitten sprites are drawn at. */
  public static final int TILE = 32;

  /** Side length of a player's (square) collision box, in pixels. */
  public static final int PLAYER_SIZE = 24;

  /** Server simulation rate. Snapshots are sent once per tick. */
  public static final int TICK_HZ = 30;

  /** Player movement speed in pixels per second. */
  public static final float PLAYER_SPEED = 170f;

  /** Full player health. */
  public static final double PLAYER_MAX_HEALTH = 100;

  /** Seconds a downed player waits before respawning at their spawn point. */
  public static final double RESPAWN_DELAY = 2.0;

  /**
   * Seconds a player who has just arrived at a spawn point cannot be hurt. Without it, respawning
   * into a wave means dying again on arrival, since a ring of enemies lands every one of its
   * attacks on the same tick.
   */
  public static final double RESPAWN_INVULNERABILITY = 2.0;

  /**
   * Seconds a player cannot be hurt again after taking a hit, which caps incoming damage at roughly
   * {@code damage / this} per second however many enemies are in contact. Deliberately shorter than
   * either attack cooldown, so it leaves a lone enemy's damage untouched and only bites on crowds.
   *
   * <p>Measured survival while ringed, from 100 health: six rats go from 1.0 s to 2.5 s, twelve from
   * 0.9 s to 2.7 s. Going much higher flattens the difficulty out — at 0.4 a ring of six deals no
   * more damage than a single rat, which makes being surrounded meaningless.
   */
  public static final double HIT_INVULNERABILITY = 0.25;

  /** Projectile entity ids start here so they never collide with player/enemy ids. */
  public static final int PROJECTILE_ID_BASE = 1_000_000;

  /** Enemy entity ids start here so they never collide with player ids. */
  public static final int ENEMY_ID_BASE = 100_000;

  /** Explosion (blast marker) entity ids start here. */
  public static final int EXPLOSION_ID_BASE = 2_000_000;

  /** Whether projectiles can hit other players. Flips off once enemies exist to shoot instead. */
  public static final boolean FRIENDLY_FIRE = false;

  /** Rat enemy configuration. */
  public static final double RAT_MAX_HEALTH = 40.0;
  public static final float RAT_SPEED = 85.0f;
  public static final double RAT_DAMAGE = 14.0;
  public static final int RAT_SIZE = 22;
  public static final double RAT_ATTACK_COOLDOWN = 0.7;

  /** Mouse enemy configuration. */
  public static final double MOUSE_MAX_HEALTH = 18.0;
  public static final float MOUSE_SPEED = 145.0f;
  public static final double MOUSE_DAMAGE = 7.0;
  public static final int MOUSE_SIZE = 16;
  public static final double MOUSE_ATTACK_COOLDOWN = 0.5;

  /**
   * How hard an enemy steers out of its neighbours' personal space, relative to the strength of its
   * chase. Above 1 on purpose: a pair settles where the push balances the pull, at roughly
   * {@code personal space / (1 + 1 / weight)}, so anything below 1 leaves bodies half-overlapped.
   * Raising it past ~1.6 buys very little, since by then the crowd is already spread as wide as the
   * target's perimeter allows.
   */
  public static final float ENEMY_SEPARATION_WEIGHT = 1.6f;

  /**
   * Personal space, as a multiple of two enemies' combined half-widths. 1 would mean "only once the
   * sprites already overlap"; a little over 1 makes them fan out just before that.
   */
  public static final float ENEMY_SEPARATION_SLACK = 1.3f;

  /**
   * Ceiling on the summed separation push. Above {@link #ENEMY_SEPARATION_WEIGHT} x this the chase
   * is outvoted, which is what lets a pile that has already formed prise itself apart; the cap stops
   * a deep overlap flinging anyone across the map.
   */
  public static final float ENEMY_SEPARATION_MAX = 3.0f;

  /** Knockback force applied to enemies on bullet hit and decay rate per second. */
  public static final float PROJECTILE_KNOCKBACK = 160.0f;
  public static final float KNOCKBACK_DECAY = 8.0f;

  /** Kitten sprite keys, assigned to players by {@code playerId % length}. */
  public static final String[] KITTEN_SPRITES = {"orange", "gray", "black", "white"};

  /** Sprite key for the given player id (client and server agree via this method). */
  public static String kittenSprite(int playerId) {
    return KITTEN_SPRITES[Math.floorMod(playerId, KITTEN_SPRITES.length)];
  }
}
