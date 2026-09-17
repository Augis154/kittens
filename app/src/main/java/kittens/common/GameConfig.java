package kittens.common;

/** Numbers both simulations must agree on. Client-only presentation values live in client/Theme. */
public final class GameConfig {
  private GameConfig() {}

  public static final int PORT = 7000;

  public static final String MAP_ID = "sewers";
  /** {@code maps/arena.txt} is the older single-screen map, kept for probes. */
  public static final String MAP_RESOURCE = "maps/sewers.txt";

  /** Pixel size of one map tile; also the size kitten sprites are drawn at. */
  public static final int TILE = 32;

  /** Server simulation rate; snapshots go out once per tick. */
  public static final int TICK_HZ = 30;
  /** The fixed step both the server and the client's predictor use per input. */
  public static final double TICK_DT = 1.0 / TICK_HZ;

  public static final int PLAYER_SIZE = 24;
  public static final float PLAYER_SPEED = 170f;
  public static final double PLAYER_MAX_HEALTH = 100;
  public static final double RESPAWN_DELAY = 2.0;
  /** Spawn grace; without it a respawn into a wave dies again on arrival. */
  public static final double RESPAWN_INVULNERABILITY = 2.0;
  /**
   * Post-hit i-frame, capping incoming damage at ~damage/this per second however many enemies are
   * in contact. Shorter than any attack cooldown, so a lone enemy is unaffected and only crowds are
   * softened (six rats: 1.0 s -> 2.5 s survival). Much higher flattens difficulty out.
   */
  public static final double HIT_INVULNERABILITY = 0.25;

  /** Id ranges per kind, so ids never collide across kinds. */
  public static final int ENEMY_ID_BASE = 100_000;
  public static final int PROJECTILE_ID_BASE = 1_000_000;
  public static final int EXPLOSION_ID_BASE = 2_000_000;
  public static final int PICKUP_ID_BASE = 3_000_000;

  /** Shared because the server collects on box overlap and the client draws the crate this size. */
  public static final int PICKUP_SIZE = 20;
  public static final double PICKUP_LIFETIME = 22.0;
  /** Deliberately short of a full heal. */
  public static final double PICKUP_HEALTH_AMOUNT = 35.0;

  /** Compile-time false: the player-hit branches it guards are intentionally dead. */
  public static final boolean FRIENDLY_FIRE = false;

  public static final double RAT_MAX_HEALTH = 40.0;
  public static final float RAT_SPEED = 85.0f;
  public static final double RAT_DAMAGE = 14.0;
  public static final int RAT_SIZE = 22;
  public static final double RAT_ATTACK_COOLDOWN = 0.7;

  public static final double MOUSE_MAX_HEALTH = 18.0;
  public static final float MOUSE_SPEED = 145.0f;
  public static final double MOUSE_DAMAGE = 7.0;
  public static final int MOUSE_SIZE = 16;
  public static final double MOUSE_ATTACK_COOLDOWN = 0.5;

  /** Separation push relative to the chase; above 1 so a pair settles apart, not half-overlapped. */
  public static final float ENEMY_SEPARATION_WEIGHT = 1.6f;
  /** Personal space as a multiple of two enemies' combined half-widths; >1 fans them out early. */
  public static final float ENEMY_SEPARATION_SLACK = 1.3f;
  /** Cap on the summed push: lets a formed pile prise apart without flinging anyone across the map. */
  public static final float ENEMY_SEPARATION_MAX = 3.0f;

  /** Knockback impulse (px/s) on bullet hit, and its decay rate per second. */
  public static final float PROJECTILE_KNOCKBACK = 160.0f;
  public static final float KNOCKBACK_DECAY = 8.0f;

  /** Kitten sprite keys, assigned by {@code playerId % length}. */
  public static final String[] KITTEN_SPRITES = {"orange", "gray", "black", "white"};

  public static String kittenSprite(int playerId) {
    return KITTEN_SPRITES[Math.floorMod(playerId, KITTEN_SPRITES.length)];
  }
}
