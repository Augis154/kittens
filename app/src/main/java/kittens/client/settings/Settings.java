package kittens.client.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The player's settings, a Singleton because they live in one file. Every part of the client that
 * reads or changes a setting must share one copy: two copies each loaded from the file would each
 * save their own view of it, and whichever saved last would silently undo the other's change (a
 * mute toggled in game, then the arsenal remembered by the menu, loses the mute). With one owner,
 * every save writes the merged state.
 *
 * <p>Created on first use, which {@code Client.main} makes happen before the window exists.
 */
public final class Settings {
  private static final Settings INSTANCE = new Settings(SettingsStore.locate());

  private final SettingsStore store;
  private final Profile profile;
  private final KeyBindings keys;
  private final AudioPrefs audio;
  private volatile DisplayPrefs display;

  private Settings(SettingsStore store) {
    this.store = store;
    Properties p = store.load();
    profile = Profile.from(p, this::save);
    keys = KeyBindings.from(p, this::save);
    audio = AudioPrefs.from(p, this::save);
    display = DisplayPrefs.from(p);
    save(); // a first run writes the defaults, so there is a file to edit
  }

  public static Settings get() {
    return INSTANCE;
  }

  public Profile profile() {
    return profile;
  }

  public KeyBindings keys() {
    return keys;
  }

  public AudioPrefs audio() {
    return audio;
  }

  public DisplayPrefs display() {
    return display;
  }

  /** A record, so a change is a replacement; see {@link DisplayPrefs} for when each part applies. */
  public void setDisplay(DisplayPrefs display) {
    this.display = display;
    save();
  }

  /** Where the settings are kept, for showing to the player. */
  public String location() {
    return store.location();
  }

  /** Writes every section, so no change can overwrite another. */
  synchronized void save() {
    Map<String, String> out = new LinkedHashMap<>();
    profile.writeTo(out);
    audio.writeTo(out);
    display.writeTo(out);
    keys.writeTo(out);
    store.save(out);
  }
}
