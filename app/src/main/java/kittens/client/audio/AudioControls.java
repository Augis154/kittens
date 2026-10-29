package kittens.client.audio;

import kittens.client.settings.AudioPrefs;
import kittens.client.settings.Settings;

/**
 * Facade over the audio settings and the sound output: changing the volume or mute is one call,
 * not "change the preference, which saves it, then decide whether to play a confirmation". The
 * keyboard shortcuts and the settings menu both used to spell that sequence out themselves.
 *
 * <p>The confirmation cue is why this is more than {@link AudioPrefs}: a volume change plays a click
 * at the new level, and unmuting plays one so it is audible that sound came back. Muting does not,
 * since nothing would be heard.
 */
public final class AudioControls {
  private AudioControls() {}

  public static void louder() {
    prefs().louder();
    confirm();
  }

  public static void quieter() {
    prefs().quieter();
    confirm();
  }

  public static void toggleMute() {
    if (!prefs().toggleMuted()) {
      confirm();
    }
  }

  private static AudioPrefs prefs() {
    return Settings.get().audio();
  }

  private static void confirm() {
    SoundSystem.get().play(Sound.SWITCH);
  }
}
