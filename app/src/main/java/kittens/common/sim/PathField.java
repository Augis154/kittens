package kittens.common.sim;

import java.util.Arrays;
import java.util.Collection;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;

/**
 * A breadth-first distance field over a {@link TileMap}'s walkable cells, measured outwards from one
 * or more goal positions. {@link #directionAt} reads the local gradient and hands back the step that
 * most reduces the distance to the nearest goal, so pursuers route around walls instead of wedging
 * against them.
 *
 * <p>One field serves every pursuer on the map: the search runs from the goals outwards, so a single
 * pass answers "which way from here?" for all of them. Immutable once built — rebuild it when the
 * goals move. Pure and free of rendering or networking, like {@link PlayerMotion}.
 */
public final class PathField {
  private static final int UNREACHABLE = Integer.MAX_VALUE;

  /** Neighbour offsets: the four orthogonal steps first, then the four diagonals. */
  private static final int[][] STEPS = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };

  private final TileMap map;
  private final int[] dist; // row-major step counts; UNREACHABLE where no route exists
  private final boolean empty;

  private PathField(TileMap map, int[] dist, boolean empty) {
    this.map = map;
    this.dist = dist;
    this.empty = empty;
  }

  /**
   * Builds the field for {@code map}, with a step count of zero on the tile under each goal.
   * Goals outside the map or inside a wall are ignored; with no usable goal left, every
   * {@link #directionAt} answers {@link Vec2#ZERO}.
   */
  public static PathField toward(TileMap map, Collection<Vec2> goals) {
    int width = map.width();
    int cells = width * map.height();
    int[] dist = new int[cells];
    Arrays.fill(dist, UNREACHABLE);

    // Each cell is enqueued at most once (it is only ever lowered from UNREACHABLE), so the ring
    // buffer can never overflow and needs no growth check.
    int[] queue = new int[cells];
    int head = 0;
    int tail = 0;

    for (Vec2 goal : goals) {
      int col = tileCol(map, goal);
      int row = tileRow(map, goal);
      if (map.isWall(col, row)) { // also covers out-of-bounds
        continue;
      }
      int index = row * width + col;
      if (dist[index] != 0) {
        dist[index] = 0;
        queue[tail++] = index;
      }
    }

    boolean empty = tail == 0;
    while (head < tail) {
      int index = queue[head++];
      int col = index % width;
      int row = index / width;
      int next = dist[index] + 1;
      for (int[] step : STEPS) {
        int nextCol = col + step[0];
        int nextRow = row + step[1];
        if (map.isWall(nextCol, nextRow) || cutsCorner(map, col, row, step)) {
          continue;
        }
        int nextIndex = nextRow * width + nextCol;
        if (dist[nextIndex] <= next) {
          continue; // breadth-first, so an already-set distance is never worse
        }
        dist[nextIndex] = next;
        queue[tail++] = nextIndex;
      }
    }
    return new PathField(map, dist, empty);
  }

  /**
   * The unit step from {@code from} toward the nearest goal, or {@link Vec2#ZERO} when the caller is
   * already on a goal tile or has no route at all. Aims at the centre of the next tile, which keeps
   * pursuers off the walls they are rounding.
   */
  public Vec2 directionAt(Vec2 from) {
    if (empty) {
      return Vec2.ZERO;
    }
    int col = tileCol(map, from);
    int row = tileRow(map, from);
    int here = distAt(col, row);
    if (here == 0) {
      return Vec2.ZERO; // same tile as a goal — the caller can close the gap directly
    }

    // Only ever move downhill. Starting at `here` also lets an enemy that has ended up inside a wall
    // or a sealed pocket (where `here` is UNREACHABLE) step out toward any cell that has a route.
    int bestDist = here;
    int bestCol = -1;
    int bestRow = -1;
    for (int[] step : STEPS) {
      int nextCol = col + step[0];
      int nextRow = row + step[1];
      if (map.isWall(nextCol, nextRow) || cutsCorner(map, col, row, step)) {
        continue;
      }
      int d = distAt(nextCol, nextRow);
      if (d < bestDist) {
        bestDist = d;
        bestCol = nextCol;
        bestRow = nextRow;
      }
    }
    if (bestCol < 0) {
      return Vec2.ZERO;
    }
    return tileCenter(map, bestCol, bestRow).sub(from).normalized();
  }

  /**
   * Whether a diagonal {@code step} out of ({@code col}, {@code row}) would squeeze past a wall
   * corner — a route the collision box would refuse to walk, so the field must not offer it.
   */
  private static boolean cutsCorner(TileMap map, int col, int row, int[] step) {
    return step[0] != 0
        && step[1] != 0
        && (map.isWall(col + step[0], row) || map.isWall(col, row + step[1]));
  }

  private int distAt(int col, int row) {
    return map.inBounds(col, row) ? dist[row * map.width() + col] : UNREACHABLE;
  }

  private static int tileCol(TileMap map, Vec2 pos) {
    return (int) Math.floor(pos.x / map.tileSize());
  }

  private static int tileRow(TileMap map, Vec2 pos) {
    return (int) Math.floor(pos.y / map.tileSize());
  }

  private static Vec2 tileCenter(TileMap map, int col, int row) {
    float size = map.tileSize();
    return Vec2.of((col + 0.5f) * size, (row + 0.5f) * size);
  }
}
