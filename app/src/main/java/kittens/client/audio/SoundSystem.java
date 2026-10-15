package kittens.client.audio;

import java.util.Arrays;
import javax.sound.sampled.LineUnavailableException;
import kittens.client.settings.AudioPrefs;
import kittens.client.settings.Settings;
import kittens.common.GameConfig;
import kittens.common.math.Vec2;

/**
 * The game's audio, a Singleton: the listener position, the voice cap and the retrigger guard are
 * process-wide state, so every caller must reach the same object. With a second instance,
 * positional sounds would be measured against whichever listener that instance happened to be told
 * about. Callers reach it through {@link #get()} rather than having it threaded through every
 * constructor that makes a noise. Volume and mute are the player's, so they live in
 * {@link Settings} and survive a restart.
 *
 * <p>With no device to open (headless, no sound server, or {@code -Dkittens.audio=off}) it stays
 * silent instead of failing, so the game and offscreen probes run anywhere.
 */
public final class SoundSystem {
  private static final SoundSystem INSTANCE = new SoundSystem();

  /** Positional sounds fade to nothing this far from the listener, a little under a viewport. */
  private static final float HEARING_RANGE = 20f * GameConfig.TILE;
  /**
   * The same sound again within this window is dropped: a shotgun's pellets, or one blast hitting
   * a pack of rats, is one event to the ear, and stacking them only clips.
   */
  private static final long RETRIGGER_NANOS = 30_000_000L;

  private final Mixer mixer; // null when silent
  private final SoundBank bank;
  private final long[] lastPlayed = new long[Sound.values().length];
  private volatile Vec2 listener = Vec2.ZERO;

  private SoundSystem() {
    mixer = "off".equals(System.getProperty("kittens.audio")) ? null : openMixer();
    bank = mixer == null ? null : new SoundBank();
    Arrays.fill(lastPlayed, System.nanoTime() - RETRIGGER_NANOS);
  }

  public static SoundSystem get() {
    return INSTANCE;
  }

  private static Mixer openMixer() {
    try {
      return Mixer.open();
    } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
      System.err.println("audio unavailable, running silent: " + e.getMessage());
      return null;
    }
  }

  /** Where the ear is, in world pixels; positional sounds are attenuated and panned against it. */
  public void listenAt(Vec2 pos) {
    listener = pos;
  }

  /** A sound with no place in the world: full volume, centred. */
  public void play(Sound sound) {
    emit(sound, 1f, 0f);
  }

  /** A sound at a world position, quieter with distance and panned toward its side of the ear. */
  public void playAt(Sound sound, Vec2 at) {
    Vec2 ear = listener;
    float dx = at.x - ear.x;
    float near = 1f - at.distance(ear) / HEARING_RANGE;
    if (near > 0f) {
      emit(sound, near * near, dx / (HEARING_RANGE * 0.5f));
    }
  }

  private synchronized void emit(Sound sound, float volume, float pan) {
    AudioPrefs prefs = Settings.get().audio();
    if (mixer == null || prefs.muted()) {
      return;
    }
    long now = System.nanoTime();
    if (now - lastPlayed[sound.ordinal()] < RETRIGGER_NANOS) {
      return;
    }
    lastPlayed[sound.ordinal()] = now;
    mixer.add(new Voice(bank.samples(sound), volume * prefs.volume(), pan));
  }
}
