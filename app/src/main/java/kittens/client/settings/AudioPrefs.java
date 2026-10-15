package kittens.client.settings;

import java.util.Map;
import java.util.Properties;

/** Master volume and mute, changed in game and kept for the next launch. */
public final class AudioPrefs {
  private static final float STEP = 0.1f;

  private volatile float volume;
  private volatile boolean muted;
  private final Runnable changed;

  private AudioPrefs(float volume, boolean muted, Runnable changed) {
    this.volume = volume;
    this.muted = muted;
    this.changed = changed;
  }

  static AudioPrefs from(Properties p, Runnable changed) {
    return new AudioPrefs((float) SettingsStore.number(p, "audio.volume", 0.8, 0, 1),
        Boolean.parseBoolean(p.getProperty("audio.muted", "false")), changed);
  }

  /** 0 to 1, applied on top of each sound's own loudness. */
  public float volume() {
    return volume;
  }

  public boolean muted() {
    return muted;
  }

  /** Flips mute and returns the new state. */
  public boolean toggleMuted() {
    muted = !muted;
    changed.run();
    return muted;
  }

  public void louder() {
    setVolume(volume + STEP);
  }

  public void quieter() {
    setVolume(volume - STEP);
  }

  private void setVolume(float v) {
    // Rounded so repeated steps land on 0.7, not 0.70000005, in the file.
    volume = Math.round(Math.clamp(v, 0f, 1f) * 10f) / 10f;
    changed.run();
  }

  void writeTo(Map<String, String> out) {
    out.put("audio.volume", Float.toString(volume));
    out.put("audio.muted", Boolean.toString(muted));
  }
}
