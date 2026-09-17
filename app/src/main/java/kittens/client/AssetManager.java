package kittens.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import kittens.common.net.EntityKind;

/** Loads images from the classpath once and shares them (a Flyweight cache); missing files get a red blob. */
final class AssetManager {
  private final Map<String, BufferedImage> cache = new HashMap<>();

  BufferedImage image(String resourceName) {
    return cache.computeIfAbsent(resourceName, AssetManager::load);
  }

  BufferedImage kitten(String sprite) {
    return image("kittens/" + sprite + ".png");
  }

  BufferedImage enemy(EntityKind kind) {
    return image("enemies/" + kind.sprite() + ".png");
  }

  BufferedImage weapon(String sprite) {
    return image("weapons/" + sprite + ".png");
  }

  private static BufferedImage load(String resourceName) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (InputStream in = cl.getResourceAsStream(resourceName)) {
      BufferedImage img = in == null ? null : ImageIO.read(in);
      return img != null ? img : fallback();
    } catch (IOException e) {
      return fallback();
    }
  }

  private static BufferedImage fallback() {
    BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setColor(new Color(200, 70, 70));
    g.fillOval(4, 4, 24, 24);
    g.dispose();
    return img;
  }
}
