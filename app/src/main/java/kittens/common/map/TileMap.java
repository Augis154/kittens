package kittens.common.map;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import kittens.common.math.Aabb;
import kittens.common.math.Vec2;

/**
 * A fixed grid of {@link Tile}s loaded from plain text, one character per cell. Square tiles of
 * {@link #tileSize()} world units, grid origin at world (0, 0). Nothing validates that a level is
 * sealed or fully connected.
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
    if (tileSize <= 0) {
      throw new IllegalArgumentException("tileSize must be positive");
    }
    List<String> rows = text.lines().toList();
    int height = rows.size();
    int width = rows.stream().mapToInt(String::length).max().orElse(0);
    if (height == 0 || width == 0) {
      throw new IllegalArgumentException("map text is empty");
    }

    Tile[][] tiles = new Tile[height][width];
    List<Vec2> spawns = new ArrayList<>();
    List<Vec2> floors = new ArrayList<>();
    for (int row = 0; row < height; row++) {
      String line = rows.get(row);
      for (int col = 0; col < width; col++) {
        Tile tile = col < line.length() ? Tile.fromGlyph(line.charAt(col)) : Tile.FLOOR;
        tiles[row][col] = tile;
        Vec2 center = Vec2.of((col + 0.5f) * tileSize, (row + 0.5f) * tileSize);
        if (tile == Tile.SPAWN) {
          spawns.add(center);
        } else if (tile == Tile.FLOOR) {
          floors.add(center);
        }
      }
    }
    return new TileMap(width, height, tileSize, tiles, spawns, floors);
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
}
