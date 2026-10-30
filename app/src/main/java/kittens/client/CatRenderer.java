package kittens.client;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import kittens.common.GameConfig;
import kittens.common.math.Vec2;

/** One layer of a kitten's client-only appearance. */
interface CatRenderer {
  record Pose(Vec2 at, BufferedImage sprite, boolean facingLeft) {
    int size() {
      return GameConfig.TILE;
    }

    int x() {
      return Math.round(at.x - size() / 2f);
    }

    int y() {
      return Math.round(at.y - size() / 2f);
    }
  }

  void draw(Graphics2D g, Pose pose);

  /** Called by the frame update, never by painting. */
  default void moved(Vec2 at, Consumer<Vec2> leaveTrail) {}
}
