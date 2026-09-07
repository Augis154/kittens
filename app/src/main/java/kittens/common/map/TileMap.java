package kittens.common.map;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;

/**
 * A fixed grid of {@link Tile}s loaded from plain text — one line per row, one character per cell.
 * Shorter rows are padded with {@link Tile#FLOOR}. All world-space queries assume square tiles of
 * {@link #tileSize()} units with the grid origin at world (0, 0).
 *
 * <pre>
 * #########
 * #S.....S#
 * #...#...#
 * #S.....S#
 * #########
 * </pre>
 */
public final class TileMap {
  private final int width;
  private final int height;
  private final float tileSize;
  private final Tile[][] tiles; // [row][col]
  private final List<Vec2> spawnPoints;

  private TileMap(int width, int height, float tileSize, Tile[][] tiles, List<Vec2> spawnPoints) {
    this.width = width;
    this.height = height;
    this.tileSize = tileSize;
    this.tiles = tiles;
    this.spawnPoints = List.copyOf(spawnPoints);
  }

  public static TileMap fromText(String text, float tileSize) {
    if (tileSize <= 0) {
      throw new IllegalArgumentException("tileSize must be positive");
    }
    List<String> rows = text.lines().toList();
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("map text is empty");
    }
    int height = rows.size();
    int width = rows.stream().mapToInt(String::length).max().orElse(0);
    if (width == 0) {
      throw new IllegalArgumentException("map has no columns");
    }

    Tile[][] tiles = new Tile[height][width];
    List<Vec2> spawns = new ArrayList<>();
    for (int row = 0; row < height; row++) {
      String line = rows.get(row);
      for (int col = 0; col < width; col++) {
        Tile tile = col < line.length() ? Tile.fromGlyph(line.charAt(col)) : Tile.FLOOR;
        tiles[row][col] = tile;
        if (tile == Tile.SPAWN) {
          spawns.add(new Vec2((col + 0.5f) * tileSize, (row + 0.5f) * tileSize));
        }
      }
    }
    return new TileMap(width, height, tileSize, tiles, spawns);
  }

  public static TileMap fromFile(Path path, float tileSize) {
    try {
      return fromText(Files.readString(path, StandardCharsets.UTF_8), tileSize);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read map file: " + path, e);
    }
  }

  /** Load a map bundled on the classpath, e.g. {@code "maps/arena.txt"}. */
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

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public float tileSize() {
    return tileSize;
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

  /** Whether the given cell blocks movement. Anything outside the map counts as a wall. */
  public boolean isWall(int col, int row) {
    return !inBounds(col, row) || tiles[row][col].blocksMovement();
  }

  /** Whether the tile covering this world position blocks movement. */
  public boolean isWallAt(Vec2 worldPos) {
    return isWall((int) Math.floor(worldPos.x / tileSize), (int) Math.floor(worldPos.y / tileSize));
  }

  /**
   * Whether a world-space box overlaps any blocking tile. Used for actor-vs-wall collision: try a
   * candidate move, and only commit it if this returns {@code false}.
   */
  public boolean overlapsWall(Aabb box) {
    int minCol = (int) Math.floor(box.minX / tileSize);
    int maxCol = (int) Math.floor((box.maxX - 1e-4f) / tileSize);
    int minRow = (int) Math.floor(box.minY / tileSize);
    int maxRow = (int) Math.floor((box.maxY - 1e-4f) / tileSize);
    for (int row = minRow; row <= maxRow; row++) {
      for (int col = minCol; col <= maxCol; col++) {
        if (isWall(col, row)) {
          return true;
        }
      }
    }
    return false;
  }

  public float pixelWidth() {
    return width * tileSize;
  }

  public float pixelHeight() {
    return height * tileSize;
  }

  /** World-space centres of every {@link Tile#SPAWN} cell, in row-major order. */
  public List<Vec2> spawnPoints() {
    return spawnPoints;
  }
}
