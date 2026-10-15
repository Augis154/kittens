package kittens.client.settings;

/** Everything a key can be bound to, with the keys it starts on; weapon slots stay on the digits. */
public enum Action {
  MOVE_UP("Move up", "W", "UP"),
  MOVE_DOWN("Move down", "S", "DOWN"),
  MOVE_LEFT("Move left", "A", "LEFT"),
  MOVE_RIGHT("Move right", "D", "RIGHT"),
  RELOAD("Reload", "R"),
  MUTE("Mute", "M"),
  VOLUME_DOWN("Volume down", "MINUS"),
  VOLUME_UP("Volume up", "EQUALS");

  /** How the settings menu names it. */
  public final String title;
  /** Java key names, as {@code javax.swing.KeyStroke} spells them. */
  final String[] defaults;

  Action(String title, String... defaults) {
    this.title = title;
    this.defaults = defaults;
  }

  /** The settings-file key, e.g. {@code key.move_up}. */
  String property() {
    return "key." + name().toLowerCase();
  }
}
