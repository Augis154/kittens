package kittens.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Loads image assets from the classpath and caches them by resource name. */
public final class AssetManager {
  private final Map<String, BufferedImage> cache = new HashMap<>();

  public BufferedImage image(String resourceName) {
    return cache.computeIfAbsent(resourceName, AssetManager::load);
  }

  /** {@code "orange"} -&gt; {@code kittens/orange.png}. */
  public BufferedImage kitten(String sprite) {
    return image("kittens/" + sprite + ".png");
  }

  /** {@code "rat"} -&gt; {@code enemies/rat.png}. */
  public BufferedImage enemy(String sprite) {
    return image("enemies/" + sprite + ".png");
  }

  /** {@code "pistol"} -&gt; {@code weapons/pistol.png}. */
  public BufferedImage weapon(String sprite) {
    return image("weapons/" + sprite + ".png");
  }

  private static BufferedImage load(String resourceName) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (InputStream in = cl.getResourceAsStream(resourceName)) {
      if (in == null) {
        return createFallback(resourceName);
      }
      BufferedImage img = ImageIO.read(in);
      if (img == null) {
        return createFallback(resourceName);
      }
      return img;
    } catch (IOException e) {
      return createFallback(resourceName);
    }
  }

  private static BufferedImage createFallback(String resourceName) {
    BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setColor(new Color(200, 70, 70));
    g.fillOval(4, 4, 24, 24);
    g.dispose();
    return img;
  }
}
