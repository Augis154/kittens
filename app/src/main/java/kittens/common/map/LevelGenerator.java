package kittens.common.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural levels assembled through {@link TileMap.Builder} (Builder pattern).
 *
 * <p>Everything the generator does is a builder call: reserve the grid, fill it solid, carve rooms,
 * connect them, mark entrances. It deliberately does not validate or derive the spawn and pickup
 * pools — that is {@link TileMap.Builder#build()}, the first point at which the whole grid exists.
 * Keeping validation out of the generator is what lets a bad level be reported as "this seed broke
 * rule X" rather than silently repaired, which would hide which rule the generator is failing; the
 * generator owns the retry policy instead, by drawing a new seed.
 *
 * <p>Connectivity is guaranteed by construction rather than luck: rooms are chained in placement
 * order, so every room is reachable from the first and build()'s connectivity check can never fire.
 * The edge-seal check can still fire, which is why placement keeps {@link #EDGE_MARGIN} cells clear —
 * a two-wide corridor reaching a centre writes one cell past it, and a centre at the margin would put
 * that cell on the border.
 *
 * <p>Seeded and deterministic: a fixed {@link Random} per call, never {@code ThreadLocalRandom}, so the
 * same seed produces the same level. The server generates once and sends the result over the wire, so
 * the client never runs this and never has to agree on a seed.
 */
public final class LevelGenerator {
  /** Same footprint as the default map, so the camera still has somewhere to scroll. */
  private static final int WIDTH = 60;
  private static final int HEIGHT = 36;

  private static final int ROOM_COUNT = 6;
  /** Fewer than this and a layout is rejected rather than shipped as a corridor with rooms in it. */
  private static final int MIN_ROOMS = 4;

  private static final int MIN_ROOM_WIDTH = 8;
  private static final int MAX_ROOM_WIDTH = 14;
  private static final int MIN_ROOM_HEIGHT = 6;
  private static final int MAX_ROOM_HEIGHT = 10;

  /**
   * Cells kept clear on every edge. Two, not one, because {@link TileMap.Builder#corridor} paints the
   * cell past its endpoint and would otherwise overwrite the seal build() checks.
   */
  private static final int EDGE_MARGIN = 2;

  /** Rooms may not touch: a shared wall would make two rooms one open cell set. */
  private static final int ROOM_GAP = 2;

  private static final int PLACEMENT_ATTEMPTS = 200;
  private static final int LAYOUT_ATTEMPTS = 40;

  /** Elbow jitter. Without it a chain of corridors can run the full width or height, and one sightline
   * dominates the level. */
  private static final int ELBOW_JITTER = 3;

  private LevelGenerator() {}

  public static TileMap generate(long seed, float tileSize) {
    Random random = new Random(seed);
    List<Room> rooms = placeRooms(random);

    TileMap.Builder level = TileMap.builder(tileSize).size(WIDTH, HEIGHT).fill(Tile.WALL);
    for (Room room : rooms) {
      level.room(room.col(), room.row(), room.width(), room.height());
    }

    // Chained in placement order, plus the closing link, so the level is a ring rather than a line:
    // a wave entering from the far side can be walked around rather than having to be run through.
    for (int i = 0; i < rooms.size(); i++) {
      Room from = rooms.get(i);
      Room to = rooms.get((i + 1) % rooms.size());
      int elbowCol = clamp(from.centredCol() + random.nextInt(-ELBOW_JITTER, ELBOW_JITTER + 1),
          EDGE_MARGIN, WIDTH - 1 - EDGE_MARGIN);
      int elbowRow = clamp(to.centredRow() + random.nextInt(-ELBOW_JITTER, ELBOW_JITTER + 1),
          EDGE_MARGIN, HEIGHT - 1 - EDGE_MARGIN);
      level.corridor(from.centredCol(), from.centredRow(), elbowCol, elbowRow);
      level.corridor(elbowCol, elbowRow, to.centredCol(), to.centredRow());
    }

    // One wave entrance per room. A spawn is not a pickup tile, so this also decides the pickup pool.
    for (Room room : rooms) {
      level.spawn(room.centredCol(), room.centredRow());
    }

    return level.build();
  }

  private static List<Room> placeRooms(Random random) {
    for (int layout = 0; layout < LAYOUT_ATTEMPTS; layout++) {
      List<Room> rooms = new ArrayList<>();
      for (int i = 0; i < ROOM_COUNT; i++) {
        Room room = tryPlace(random, rooms);
        if (room == null) {
          break;
        }
        rooms.add(room);
      }
      if (rooms.size() >= MIN_ROOMS) {
        return rooms;
      }
    }
    throw new IllegalStateException("could not place " + MIN_ROOMS + " rooms in " + LAYOUT_ATTEMPTS
        + " layouts on a " + WIDTH + "x" + HEIGHT + " grid");
  }

  private static Room tryPlace(Random random, List<Room> placed) {
    for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
      int width = random.nextInt(MIN_ROOM_WIDTH, MAX_ROOM_WIDTH + 1);
      int height = random.nextInt(MIN_ROOM_HEIGHT, MAX_ROOM_HEIGHT + 1);
      int col = random.nextInt(EDGE_MARGIN, WIDTH - EDGE_MARGIN - width + 1);
      int row = random.nextInt(EDGE_MARGIN, HEIGHT - EDGE_MARGIN - height + 1);
      Room room = new Room(col, row, width, height);
      boolean clear = true;
      for (Room other : placed) {
        if (room.overlaps(other, ROOM_GAP)) {
          clear = false;
          break;
        }
      }
      if (clear) {
        return room;
      }
    }
    return null;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private record Room(int col, int row, int width, int height) {
    /** One cell in from the true centre, so a two-wide corridor stays inside the room. */
    int centredCol() {
      return col + width / 2;
    }

    int centredRow() {
      return row + height / 2;
    }

    boolean overlaps(Room other, int gap) {
      return col - gap < other.col + other.width && other.col - gap < col + width
          && row - gap < other.row + other.height && other.row - gap < row + height;
    }
  }
}
