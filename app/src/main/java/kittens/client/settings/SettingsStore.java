package kittens.client.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;

/**
 * The settings file on disk: {@code ~/.kittens/settings.properties}, or the path in
 * {@code -Dkittens.settings} ({@code off} keeps everything in memory). Saves go to a temporary file
 * that is then moved over the old one, so a crash mid-save never leaves half a file.
 */
final class SettingsStore {
  private static final String HEADER = """
      # Kittens player settings. The settings menu (S on the arsenal screen, Esc in game) rewrites
      # this file, so edit it by hand only while the game is closed. Keys take Java key names
      # (W, UP, SPACE, MINUS...), comma-separated.
      """;

  private final Path file; // null = not persisted

  private SettingsStore(Path file) {
    this.file = file;
  }

  static SettingsStore locate() {
    String override = System.getProperty("kittens.settings");
    if ("off".equals(override)) {
      return new SettingsStore(null);
    }
    return new SettingsStore(override != null ? Path.of(override)
        : Path.of(System.getProperty("user.home"), ".kittens", "settings.properties"));
  }

  String location() {
    return file == null ? "not saved (kittens.settings=off)" : file.toString();
  }

  /** Whatever the file holds; empty when it is missing or unreadable, so defaults apply. */
  Properties load() {
    Properties p = new Properties();
    if (file != null && Files.exists(file)) {
      try (Reader in = Files.newBufferedReader(file)) {
        p.load(in);
      } catch (IOException | IllegalArgumentException e) {
        System.err.println("settings unreadable, using defaults: " + e.getMessage());
      }
    }
    return p;
  }

  /** Writes the entries in the order given, which groups them the way the file reads best. */
  void save(Map<String, String> entries) {
    if (file == null) {
      return;
    }
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      try (Writer out = Files.newBufferedWriter(tmp)) {
        out.write(HEADER);
        for (Map.Entry<String, String> e : entries.entrySet()) {
          out.write(e.getKey() + "=" + e.getValue() + "\n");
        }
      }
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException | UncheckedIOException e) {
      System.err.println("settings not saved: " + e.getMessage());
    }
  }

  /** A number from the file, falling back to {@code fallback} when missing or garbled. */
  static double number(Properties p, String key, double fallback, double min, double max) {
    try {
      return Math.clamp(Double.parseDouble(p.getProperty(key, "")), min, max);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
