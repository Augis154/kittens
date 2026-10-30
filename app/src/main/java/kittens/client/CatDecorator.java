package kittens.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.function.Consumer;
import kittens.common.math.Vec2;

/** Delegates to the wrapped appearance, then lets a concrete layer add its own drawing. */
abstract class CatDecorator implements CatRenderer {
  protected final CatRenderer wrapped;

  CatDecorator(CatRenderer wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public void draw(Graphics2D g, Pose pose) {
    wrapped.draw(g, pose);
  }

  @Override
  public void moved(Vec2 at, Consumer<Vec2> leaveTrail) {
    wrapped.moved(at, leaveTrail);
  }

  /** Tint one opaque body pixel, keeping the sprite's outline and selected coat colour intact. */
  protected static void pixel(Graphics2D g, Pose pose, int sx, int sy, Color tint, float strength) {
    if (sy < 1 || sy >= pose.sprite().getHeight() - 1 || sx <= 0
        || sx >= pose.sprite().getWidth() - 1) {
      return;
    }
    // All four kitten sprites share this silhouette. Stay one pixel inside it.
    if ((pose.sprite().getRGB(sx, sy) >>> 24) == 0
        || (pose.sprite().getRGB(sx - 1, sy) >>> 24) == 0
        || (pose.sprite().getRGB(sx + 1, sy) >>> 24) == 0) {
      return;
    }
    Color coat = new Color(pose.sprite().getRGB(sx, sy), true);
    g.setColor(tint);
    int x = pose.facingLeft() ? pose.size() - 1 - sx : sx;
    g.fillRect(pose.x() + x, pose.y() + sy, 1, 1);
  }
}
