package kittens.client;

import java.awt.Graphics2D;

/** The original coloured kitten sprite, before any wound layers. */
final class BasicCatRenderer implements CatRenderer {
  @Override
  public void draw(Graphics2D g, Pose pose) {
    int x = pose.x();
    int y = pose.y();
    int size = pose.size();
    if (pose.facingLeft()) {
      g.drawImage(pose.sprite(), x + size, y, -size, size, null);
    } else {
      g.drawImage(pose.sprite(), x, y, size, size, null);
    }
  }
}
