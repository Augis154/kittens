package kittens.common.map;

/** The kinds of cell a {@link TileMap} can hold. */
public enum Tile {
  FLOOR('.'),
  WALL('#'),
  /** Walkable floor that also marks a player/enemy spawn location. */
  SPAWN('S');

  private final char glyph;

  Tile(char glyph) {
    this.glyph = glyph;
  }

  public char glyph() {
    return glyph;
  }

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
