package kittens.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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

  /** {@code "pistol"} -&gt; {@code weapons/pistol.png}. */
  public BufferedImage weapon(String sprite) {
    return image("weapons/" + sprite + ".png");
  }

  private static BufferedImage load(String resourceName) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (InputStream in = cl.getResourceAsStream(resourceName)) {
      if (in == null) {
        throw new IllegalArgumentException("asset not found on classpath: " + resourceName);
      }
      BufferedImage img = ImageIO.read(in);
      if (img == null) {
        throw new IllegalArgumentException("not a readable image: " + resourceName);
      }
      return img;
    } catch (IOException e) {
      throw new UncheckedIOException("could not read asset: " + resourceName, e);
    }
  }
}
