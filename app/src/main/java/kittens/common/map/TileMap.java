package kittens.common.map;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;

/**
 * A fixed grid of {@link Tile}s. Square tiles of {@link #tileSize()} world units, grid origin at
 * world (0, 0). Immutable once built.
 *
 * <p>Every map comes from a {@link Builder} (Builder pattern): {@link #fromText} and
 * {@link #fromResource} are the text path and {@link LevelGenerator} the procedural one, and both
 * route through the same builder. {@link Builder#build()} is the only place a level is validated
 * and the only place {@link #spawnPoints()} and {@link #floorPoints()} can be derived — a level
 * assembled cell by cell has no parse loop to derive them in, and {@code floorPoints} is the pool
 * {@code PickupDirector} samples, so a built level cannot feed the simulation without it.
 */
public final class TileMap {
  private final int width;
  private final int height;
  private final float tileSize;
  private final Tile[][] tiles; // [row][col]
  private final List<Vec2> spawnPoints;
  private final List<Vec2> floorPoints;

  private TileMap(int width, int height, float tileSize, Tile[][] tiles,
      List<Vec2> spawnPoints, List<Vec2> floorPoints) {
    this.width = width;
    this.height = height;
    this.tileSize = tileSize;
    this.tiles = tiles;
    this.spawnPoints = List.copyOf(spawnPoints);
    this.floorPoints = List.copyOf(floorPoints);
  }

  /** Parses one line per row; shorter rows are padded with floor. */
  public static TileMap fromText(String text, float tileSize) {
    return builder(tileSize).parse(text).build();
  }

  /** Loads a map bundled on the classpath, e.g. {@code "maps/sewers.txt"}. */
  public static TileMap fromResource(String resourceName, float tileSize) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (InputStream in = cl.getResourceAsStream(resourceName)) {
      if (in == null) {
        throw new IllegalArgumentException("map resource not found on classpath: " + resourceName);
      }
      return fromText(new String(in.readAllBytes(), StandardCharsets.UTF_8), tileSize);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read map resource: " + resourceName, e);
    }
  }

  public static Builder builder(float tileSize) {
    return new Builder(tileSize);
  }

  /** Glyph rows, the shape a level travels on the wire as; {@link #fromText} is the inverse. */
  public List<String> rows() {
    List<String> rows = new ArrayList<>(height);
    for (int row = 0; row < height; row++) {
      StringBuilder line = new StringBuilder(width);
      for (int col = 0; col < width; col++) {
        line.append(tiles[row][col].glyph());
      }
      rows.add(line.toString());
    }
    return rows;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public float tileSize() {
    return tileSize;
  }

  public float pixelWidth() {
    return width * tileSize;
  }

  public float pixelHeight() {
    return height * tileSize;
  }

  /** Column of the tile covering world x. May be outside the map. */
  public int colAt(float x) {
    return (int) Math.floor(x / tileSize);
  }

  /** Row of the tile covering world y. May be outside the map. */
  public int rowAt(float y) {
    return (int) Math.floor(y / tileSize);
  }

  public Vec2 cellCenter(int col, int row) {
    return Vec2.of((col + 0.5f) * tileSize, (row + 0.5f) * tileSize);
  }

  public boolean inBounds(int col, int row) {
    return col >= 0 && col < width && row >= 0 && row < height;
  }

  public Tile tileAt(int col, int row) {
    if (!inBounds(col, row)) {
      throw new IndexOutOfBoundsException("tile (" + col + ", " + row + ") is outside the map");
    }
    return tiles[row][col];
  }

  /** Whether the cell blocks movement. Anything outside the map counts as a wall. */
  public boolean isWall(int col, int row) {
    return !inBounds(col, row) || tiles[row][col].blocksMovement();
  }

  public boolean isWallAt(Vec2 worldPos) {
    return isWall(colAt(worldPos.x), rowAt(worldPos.y));
  }

  /** Whether a world-space box overlaps any blocking tile. */
  public boolean overlapsWall(Aabb box) {
    int maxCol = colAt(box.maxX - 1e-4f);
    int maxRow = rowAt(box.maxY - 1e-4f);
    for (int row = rowAt(box.minY); row <= maxRow; row++) {
      for (int col = colAt(box.minX); col <= maxCol; col++) {
        if (isWall(col, row)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Centres of every {@link Tile#SPAWN} cell, row-major. */
  public List<Vec2> spawnPoints() {
    return spawnPoints;
  }

  /** Centres of every plain {@link Tile#FLOOR} cell (not spawns), row-major; the pickup pool. */
  public List<Vec2> floorPoints() {
    return floorPoints;
  }

  /**
   * The mutable construction phase of a {@link TileMap}. Cells default to floor, so a generator
   * typically fills with wall and carves; {@link #build()} is where the level becomes a
   * {@link TileMap}, and the only point at which the whole grid exists.
   */
  public static final class Builder {
    private final float tileSize;
    private int width;
    private int height;
    private Tile[][] tiles; // [row][col], grown on demand

    private Builder(float tileSize) {
      if (tileSize <= 0) {
        throw new IllegalArgumentException("tileSize must be positive");
      }
      this.tileSize = tileSize;
      this.tiles = new Tile[0][0];
    }

    /** Reserves at least this many columns and rows. Call before {@link #fill}. */
    public Builder size(int width, int height) {
      if (width <= 0 || height <= 0) {
        throw new IllegalArgumentException("level size must be positive: " + width + "x" + height);
      }
      grow(Math.max(width, this.width), Math.max(height, this.height));
      return this;
    }

    /** Paints every reserved cell. */
    public Builder fill(Tile tile) {
      for (int row = 0; row < height; row++) {
        for (int col = 0; col < width; col++) {
          tiles[row][col] = tile;
        }
      }
      return this;
    }

    /** Paints one cell, growing the grid to cover it. */
    public Builder set(int col, int row, Tile tile) {
      if (col < 0 || row < 0) {
        throw new IllegalArgumentException("cell is negative: (" + col + ", " + row + ")");
      }
      grow(Math.max(col + 1, width), Math.max(row + 1, height));
      tiles[row][col] = tile;
      return this;
    }

    public Tile tileAt(int col, int row) {
      return inBounds(col, row) ? tiles[row][col] : Tile.FLOOR;
    }

    public boolean inBounds(int col, int row) {
      return col >= 0 && col < width && row >= 0 && row < height;
    }

    /** Carves a {@code w × h} rectangle of floor. */
    public Builder room(int col, int row, int w, int h) {
      for (int r = row; r < row + h; r++) {
        for (int c = col; c < col + w; c++) {
          set(c, r, Tile.FLOOR);
        }
      }
      return this;
    }

    /**
     * Carves an L-shaped path two cells wide, so a rat's 22px box still fits inside a 32px tile.
     * Two-wide is the narrowest a corridor can be and still be walkable, so nothing narrower is
     * ever produced here.
     */
    public Builder corridor(int fromCol, int fromRow, int toCol, int toRow) {
      for (int col = Math.min(fromCol, toCol); col <= Math.max(fromCol, toCol); col++) {
        set(col, fromRow, Tile.FLOOR);
        set(col, fromRow + 1, Tile.FLOOR);
      }
      for (int row = Math.min(fromRow, toRow); row <= Math.max(fromRow, toRow); row++) {
        set(toCol, row, Tile.FLOOR);
        set(toCol + 1, row, Tile.FLOOR);
      }
      return this;
    }

    /** Marks a wave entrance. A spawn is deliberately not in the pickup pool. */
    public Builder spawn(int col, int row) {
      return set(col, row, Tile.SPAWN);
    }

    /** Parses one line per row; shorter rows are padded with floor. */
    public Builder parse(String text) {
      List<String> lines = text.lines().toList();
      int rows = 0;
      int cols = 0;
      for (String line : lines) {
        rows++;
        cols = Math.max(cols, line.length());
      }
      // Grow before writing: the fields are what grow compares against, so updating them first would
      // make it think the grid is already big enough.
      grow(Math.max(cols, width), Math.max(rows, height));
      for (int row = 0; row < height; row++) {
        String line = row < lines.size() ? lines.get(row) : "";
        for (int col = 0; col < width; col++) {
          tiles[row][col] = col < line.length() ? Tile.fromGlyph(line.charAt(col)) : Tile.FLOOR;
        }
      }
      return this;
    }

    /** Glyph rows of what has been built so far, the same shape {@link TileMap#rows()} produces. */
    public List<String> rows() {
      List<String> rows = new ArrayList<>(height);
      for (int row = 0; row < height; row++) {
        StringBuilder line = new StringBuilder(width);
        for (int col = 0; col < width; col++) {
          line.append(tiles[row][col].glyph());
        }
        rows.add(line.toString());
      }
      return rows;
    }

    /**
     * Validates, derives, and freezes. The three rules a level has to satisfy are checked here
     * because this is the first moment the whole grid exists: rectangular, sealed by walls on every
     * edge, and all open cells mutually reachable. A ragged parsed row is padded with floor and then
     * fails the seal check — that is intentional, a ragged level is a broken level.
     *
     * <p>Corridor width is not checked: it is a design preference, not a property a grid can be
     * tested for, so the generator is the thing that guarantees it.
     */
    public TileMap build() {
      if (width == 0 || height == 0) {
        throw new IllegalArgumentException("level is empty");
      }
      for (int row = 0; row < height; row++) {
        if (tiles[row][0] != Tile.WALL || tiles[row][width - 1] != Tile.WALL) {
          throw new IllegalArgumentException("level is not sealed on the left/right edge (row "
              + row + ")");
        }
      }
      for (int col = 0; col < width; col++) {
        if (tiles[0][col] != Tile.WALL || tiles[height - 1][col] != Tile.WALL) {
          throw new IllegalArgumentException("level is not sealed on the top/bottom edge (col "
              + col + ")");
        }
      }

      List<Vec2> spawns = new ArrayList<>();
      List<Vec2> floors = new ArrayList<>();
      int openCells = 0;
      for (int row = 0; row < height; row++) {
        for (int col = 0; col < width; col++) {
          Tile tile = tiles[row][col];
          if (tile == Tile.WALL) {
            continue;
          }
          openCells++;
          Vec2 center = Vec2.of((col + 0.5f) * tileSize, (row + 0.5f) * tileSize);
          if (tile == Tile.SPAWN) {
            spawns.add(center);
          } else {
            floors.add(center);
          }
        }
      }
      if (openCells == 0) {
        throw new IllegalArgumentException("level has no open cells");
      }
      // A spawn is required because GameWorld.addPlayer indexes spawnPoints directly, and a
      // walled-off room would otherwise strand waves and pickups in it silently.
      if (spawns.isEmpty()) {
        throw new IllegalArgumentException("level has no spawn points");
      }
      int reachable = countReachable();
      if (reachable != openCells) {
        throw new IllegalArgumentException("level has " + (openCells - reachable)
            + " unreachable open cell(s) out of " + openCells);
      }

      Tile[][] frozen = new Tile[height][width];
      for (int row = 0; row < height; row++) {
        System.arraycopy(tiles[row], 0, frozen[row], 0, width);
      }
      return new TileMap(width, height, tileSize, frozen, spawns, floors);
    }

    /** Flood fill from the first open cell; the count is what build() compares against. */
    private int countReachable() {
      int startRow = -1;
      int startCol = -1;
      for (int row = 0; row < height && startRow < 0; row++) {
        for (int col = 0; col < width; col++) {
          if (tiles[row][col] != Tile.WALL) {
            startRow = row;
            startCol = col;
            break;
          }
        }
      }
      boolean[][] seen = new boolean[height][width];
      Deque<long[]> queue = new ArrayDeque<>();
      queue.add(new long[] {startRow, startCol});
      int count = 0;
      while (!queue.isEmpty()) {
        long[] cell = queue.poll();
        int row = (int) cell[0];
        int col = (int) cell[1];
        if (seen[row][col] || tiles[row][col] == Tile.WALL) {
          continue;
        }
        seen[row][col] = true;
        count++;
        for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
          int nr = row + step[0];
          int nc = col + step[1];
          if (nr >= 0 && nr < height && nc >= 0 && nc < width && !seen[nr][nc]) {
            queue.add(new long[] {nr, nc});
          }
        }
      }
      return count;
    }

    private void grow(int newWidth, int newHeight) {
      newWidth = Math.max(newWidth, width);
      newHeight = Math.max(newHeight, height);
      // The grid is allocated lazily, so an unchanged size still has to allocate the first time.
      if (tiles.length > 0 && newWidth == width && newHeight == height) {
        return;
      }
      Tile[][] grown = new Tile[newHeight][newWidth];
      for (int row = 0; row < newHeight; row++) {
        for (int col = 0; col < newWidth; col++) {
          grown[row][col] = row < height && col < width ? tiles[row][col] : Tile.FLOOR;
        }
      }
      tiles = grown;
      height = newHeight;
      width = newWidth;
    }
  }
}
