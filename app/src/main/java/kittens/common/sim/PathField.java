package kittens.common.sim;

import java.util.Arrays;
import java.util.Collection;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;

/**
 * Breadth-first distance field over the walkable tiles, measured outwards from every goal, so one
 * pass answers "which way to the nearest goal?" for every pursuer. Immutable: rebuild when goals move.
 */
public final class PathField {
  private static final int UNREACHABLE = Integer.MAX_VALUE;

  /** Four orthogonal steps, then the four diagonals. */
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

  /** Goals outside the map or inside a wall are ignored; with none left every direction is zero. */
  public static PathField toward(TileMap map, Collection<Vec2> goals) {
    int width = map.width();
    int cells = width * map.height();
    int[] dist = new int[cells];
    Arrays.fill(dist, UNREACHABLE);

    // Each cell is enqueued at most once, so a plain array is a big enough queue.
    int[] queue = new int[cells];
    int head = 0;
    int tail = 0;
    for (Vec2 goal : goals) {
      int col = map.colAt(goal.x);
      int row = map.rowAt(goal.y);
      if (map.isWall(col, row)) {
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
          continue;
        }
        dist[nextIndex] = next;
        queue[tail++] = nextIndex;
      }
    }
    return new PathField(map, dist, empty);
  }

  /**
   * Unit step from {@code from} toward the nearest goal, aimed at the centre of the next tile, or
   * zero when already on a goal tile or without a route. Only ever moves downhill, which also lets
   * a body stuck inside a wall step out toward any routed cell.
   */
  public Vec2 directionAt(Vec2 from) {
    if (empty) {
      return Vec2.ZERO;
    }
    int col = map.colAt(from.x);
    int row = map.rowAt(from.y);
    int here = distAt(col, row);
    if (here == 0) {
      return Vec2.ZERO;
    }
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
    return bestCol < 0 ? Vec2.ZERO : map.cellCenter(bestCol, bestRow).sub(from).normalized();
  }

  /** A diagonal past a wall corner is a route the collision box would refuse to walk. */
  private static boolean cutsCorner(TileMap map, int col, int row, int[] step) {
    return step[0] != 0 && step[1] != 0
        && (map.isWall(col + step[0], row) || map.isWall(col, row + step[1]));
  }

  private int distAt(int col, int row) {
    return map.inBounds(col, row) ? dist[row * map.width() + col] : UNREACHABLE;
  }
}
