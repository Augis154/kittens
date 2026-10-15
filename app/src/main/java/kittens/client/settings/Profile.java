package kittens.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import kittens.common.GameConfig;

/**
 * Who is playing: the name and kitten colour other players see, and the arsenal the menu should
 * offer first. The client sends name and colour on joining and again whenever the settings menu
 * closes, so a change shows in a running match.
 */
public final class Profile {
  /** {@code AUTO} lets the server pick by join order, which keeps a fresh match colourful. */
  public static final String AUTO = "auto";

  private volatile String name;
  private volatile String color;
  private volatile String arsenal;
  private final Runnable changed;

  private Profile(String name, String color, String arsenal, Runnable changed) {
    this.name = name;
    this.color = color;
    this.arsenal = arsenal;
    this.changed = changed;
  }

  /** {@link #AUTO} followed by every kitten sprite, in the order the menu steps through them. */
  public static List<String> colors() {
    List<String> all = new ArrayList<>(List.of(AUTO));
    all.addAll(List.of(GameConfig.KITTEN_SPRITES));
    return all;
  }

  /** The name defaults to the OS login, so a fresh install already tells players apart. */
  static Profile from(Properties p, Runnable changed) {
    String name = p.getProperty("player.name", System.getProperty("user.name", ""));
    String color = p.getProperty("player.color", AUTO);
    return new Profile(GameConfig.cleanName(name), colors().contains(color) ? color : AUTO,
        p.getProperty("player.arsenal", ""), changed);
  }

  public String name() {
    return name;
  }

  /** Cleaned the same way the server cleans it, so the menu shows what other players will get. */
  public void setName(String raw) {
    String cleaned = GameConfig.cleanName(raw);
    if (!cleaned.equals(name)) {
      name = cleaned;
      changed.run();
    }
  }

  /** A kitten sprite name, or {@link #AUTO}. */
  public String color() {
    return color;
  }

  public void setColor(String color) {
    if (colors().contains(color) && !color.equals(this.color)) {
      this.color = color;
      changed.run();
    }
  }

  /** {@code WeaponFactory.name()} of the last arsenal picked, or empty before the first pick. */
  public String arsenal() {
    return arsenal;
  }

  public void setArsenal(String arsenal) {
    if (!arsenal.equals(this.arsenal)) {
      this.arsenal = arsenal;
      changed.run();
    }
  }

  void writeTo(Map<String, String> out) {
    out.put("player.name", name);
    out.put("player.color", color);
    out.put("player.arsenal", arsenal);
  }
}
