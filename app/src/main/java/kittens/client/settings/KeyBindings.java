package kittens.client.settings;

import java.awt.AWTKeyStroke;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.swing.KeyStroke;

/**
 * Which keys trigger each {@link Action}. Every action always has at least one key: rebinding a key
 * that another action uses moves it, and an action left with none takes the old key back. EDT only.
 */
public final class KeyBindings {
  /** Keys the toolkit names in words ("Minus") that read better as the symbol on the keycap. */
  private static final Map<Integer, String> SYMBOLS = Map.of(KeyEvent.VK_MINUS, "-",
      KeyEvent.VK_EQUALS, "=", KeyEvent.VK_COMMA, ",", KeyEvent.VK_PERIOD, ".",
      KeyEvent.VK_SLASH, "/", KeyEvent.VK_SEMICOLON, ";", KeyEvent.VK_QUOTE, "'",
      KeyEvent.VK_OPEN_BRACKET, "[", KeyEvent.VK_CLOSE_BRACKET, "]", KeyEvent.VK_BACK_SLASH, "\\");

  private final Map<Action, List<Integer>> keys = new EnumMap<>(Action.class);
  private final Runnable changed;

  private KeyBindings(Properties p, Runnable changed) {
    this.changed = changed;
    for (Action a : Action.values()) {
      List<Integer> codes = parse(p.getProperty(a.property(), ""));
      keys.put(a, codes.isEmpty() ? defaults(a) : codes);
    }
  }

  static KeyBindings from(Properties p, Runnable changed) {
    return new KeyBindings(p, changed);
  }

  /** Makes {@code keyCode} the only key for {@code action}, taking it from whichever action had it. */
  public void rebind(Action action, int keyCode) {
    List<Integer> previous = keys.get(action);
    for (Action other : Action.values()) {
      List<Integer> codes = keys.get(other);
      if (other != action && codes.contains(keyCode)) {
        List<Integer> rest = new ArrayList<>(codes);
        rest.remove(Integer.valueOf(keyCode));
        // The displaced action inherits this one's old key rather than being left unbound.
        keys.put(other, rest.isEmpty() ? List.of(previous.get(0)) : List.copyOf(rest));
      }
    }
    keys.put(action, List.of(keyCode));
    changed.run();
  }

  public void resetToDefaults() {
    for (Action a : Action.values()) {
      keys.put(a, defaults(a));
    }
    changed.run();
  }

  public boolean is(Action action, int keyCode) {
    return keys.get(action).contains(keyCode);
  }

  /** The first bound key as the keyboard labels it, for on-screen hints. */
  public String label(Action action) {
    return symbol(keys.get(action).get(0));
  }

  /** Every bound key, for the settings menu: "W / Up". */
  public String labels(Action action) {
    return keys.get(action).stream().map(this::symbol).collect(Collectors.joining(" / "));
  }

  private String symbol(int code) {
    return SYMBOLS.getOrDefault(code, KeyEvent.getKeyText(code));
  }

  void writeTo(Map<String, String> out) {
    for (Action a : Action.values()) {
      out.put(a.property(), keys.get(a).stream().map(KeyBindings::name)
          .collect(Collectors.joining(",")));
    }
  }

  private static List<Integer> defaults(Action a) {
    return parse(String.join(",", a.defaults));
  }

  /** Unknown names are skipped, so one typo costs that key rather than the whole binding. */
  private static List<Integer> parse(String raw) {
    List<Integer> codes = new ArrayList<>();
    for (String name : raw.split(",")) {
      KeyStroke stroke = name.isBlank() ? null : KeyStroke.getKeyStroke(name.trim().toUpperCase());
      if (stroke != null && stroke.getKeyCode() != KeyEvent.VK_UNDEFINED) {
        codes.add(stroke.getKeyCode());
      }
    }
    return List.copyOf(codes);
  }

  private static String name(int keyCode) {
    return AWTKeyStroke.getAWTKeyStroke(keyCode, 0).toString().replace("pressed ", "");
  }
}
