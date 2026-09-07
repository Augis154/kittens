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

  public static final float PROJECTILE_SPEED = 430f;
  public static final double PROJECTILE_DAMAGE = 18;
  public static final double PROJECTILE_LIFETIME = 1.4;

  /** Minimum seconds between shots while the fire button is held. */
  public static final double FIRE_INTERVAL = 0.22;

  /** Full player health. */
  public static final double PLAYER_MAX_HEALTH = 100;

  /** Seconds a downed player waits before respawning at their spawn point. */
  public static final double RESPAWN_DELAY = 2.0;

  /** Projectile entity ids start here so they never collide with player ids. */
  public static final int PROJECTILE_ID_BASE = 1_000_000;

  /** Whether projectiles can hit other players. Flips off once enemies exist to shoot instead. */
  public static final boolean FRIENDLY_FIRE = true;

  /** Kitten sprite keys, assigned to players by {@code playerId % length}. */
  public static final String[] KITTEN_SPRITES = {"orange", "gray", "black", "white"};

  /** Sprite key for the given player id (client and server agree via this method). */
  public static String kittenSprite(int playerId) {
    return KITTEN_SPRITES[Math.floorMod(playerId, KITTEN_SPRITES.length)];
  }
}
