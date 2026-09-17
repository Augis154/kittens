package kittens.common.map;

/** The kinds of cell a {@link TileMap} can hold. */
public enum Tile {
  FLOOR,
  WALL,
  /** Walkable floor that is also a player/enemy spawn location. */
  SPAWN;

  public boolean blocksMovement() {
    return this == WALL;
  }

  public static Tile fromGlyph(char c) {
    return switch (c) {
      case '.', ' ' -> FLOOR;
      case '#' -> WALL;
      case 'S', 's' -> SPAWN;
      default -> throw new IllegalArgumentException("unknown tile glyph: '" + c + "'");
    };
  }
}
