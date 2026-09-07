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

  /** Kitten sprite keys, assigned to players by {@code playerId % length}. */
  public static final String[] KITTEN_SPRITES = {"orange", "gray", "black", "white"};

  /** Sprite key for the given player id (client and server agree via this method). */
  public static String kittenSprite(int playerId) {
    return KITTEN_SPRITES[Math.floorMod(playerId, KITTEN_SPRITES.length)];
  }
}
