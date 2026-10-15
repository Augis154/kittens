package kittens.client.audio;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Synthesizes every {@link Sound} once at startup into mono PCM that all voices share. Built from
 * decaying filtered noise and pitch-gliding tones, so there are no audio assets to ship or miss.
 */
final class SoundBank {
  static final float SAMPLE_RATE = 22050f;
  /** Fade applied to every sound's tail so a cut-off never clicks. */
  private static final double TAIL_FADE = 0.006;

  private final Map<Sound, short[]> samples = new EnumMap<>(Sound.class);

  SoundBank() {
    for (Sound s : Sound.values()) {
      samples.put(s, synthesize(s));
    }
  }

  short[] samples(Sound sound) {
    return samples.get(sound);
  }

  private static short[] synthesize(Sound sound) {
    Random rnd = new Random(sound.ordinal()); // fixed seed: the same sound on every launch
    // A switch expression must cover every Sound, so a constant with no recipe fails to compile.
    return toPcm(switch (sound) {
      case PISTOL -> {
        float[] b = buffer(0.18);
        noise(b, rnd, 0, 0.030, 4000, 0.75f);
        tone(b, 0, 220, 90, 0.05, 0.55f, false);
        yield b;
      }
      case RIFLE -> {
        float[] b = buffer(0.12);
        noise(b, rnd, 0, 0.018, 6000, 0.6f);
        tone(b, 0, 300, 140, 0.03, 0.35f, false);
        yield b;
      }
      case SHOTGUN -> {
        float[] b = buffer(0.35);
        noise(b, rnd, 0, 0.070, 2500, 0.95f);
        tone(b, 0, 140, 55, 0.09, 0.7f, false);
        yield b;
      }
      case LAUNCH -> {
        float[] b = buffer(0.40);
        noise(b, rnd, 0, 0.150, 1200, 0.5f);
        tone(b, 0, 90, 240, 0.20, 0.4f, false);
        yield b;
      }
      case BOOM -> {
        float[] b = buffer(1.0);
        noise(b, rnd, 0, 0.280, 600, 1.0f);
        noise(b, rnd, 0, 0.060, 5000, 0.35f);
        tone(b, 0, 70, 30, 0.35, 0.9f, false);
        yield b;
      }
      case HIT -> {
        float[] b = buffer(0.08);
        tone(b, 0, 380, 160, 0.025, 0.5f, false);
        noise(b, rnd, 0, 0.010, 3000, 0.3f);
        yield b;
      }
      case SQUEAK -> {
        float[] b = buffer(0.17);
        tone(b, 0, 1800, 2600, 0.05, 0.3f, false);
        tone(b, 0.06, 2600, 1500, 0.04, 0.3f, false);
        yield b;
      }
      case HURT -> {
        float[] b = buffer(0.24);
        tone(b, 0, 650, 820, 0.05, 0.35f, true);
        tone(b, 0.04, 820, 520, 0.10, 0.4f, true);
        yield b;
      }
      case HEAL -> {
        float[] b = buffer(0.32);
        tone(b, 0, 660, 660, 0.08, 0.3f, true);
        tone(b, 0.07, 880, 880, 0.08, 0.3f, true);
        tone(b, 0.14, 1320, 1320, 0.10, 0.3f, true);
        yield b;
      }
      case AMMO -> {
        float[] b = buffer(0.20);
        noise(b, rnd, 0, 0.008, 7000, 0.5f);
        tone(b, 0, 1800, 1700, 0.02, 0.2f, false);
        noise(b, rnd, 0.09, 0.008, 7000, 0.5f);
        tone(b, 0.09, 2200, 2100, 0.02, 0.2f, false);
        yield b;
      }
      case RELOAD -> {
        float[] b = buffer(0.26);
        noise(b, rnd, 0, 0.012, 3500, 0.45f);
        tone(b, 0, 900, 800, 0.015, 0.25f, false);
        noise(b, rnd, 0.14, 0.012, 3500, 0.45f);
        tone(b, 0.14, 1200, 1100, 0.015, 0.25f, false);
        yield b;
      }
      case SWITCH -> {
        float[] b = buffer(0.05);
        tone(b, 0, 2200, 1800, 0.008, 0.25f, false);
        noise(b, rnd, 0, 0.004, 8000, 0.2f);
        yield b;
      }
    });
  }

  private static float[] buffer(double seconds) {
    return new float[(int) (seconds * SAMPLE_RATE)];
  }

  /** Adds white noise through a one-pole low-pass, decaying exponentially from {@code start}. */
  private static void noise(float[] b, Random rnd, double start, double decay, double cutoffHz,
      float gain) {
    float a = (float) (1 - Math.exp(-2 * Math.PI * cutoffHz / SAMPLE_RATE));
    float y = 0f;
    for (int i = (int) (start * SAMPLE_RATE); i < b.length; i++) {
      double t = i / SAMPLE_RATE - start;
      y += a * ((rnd.nextFloat() * 2 - 1) - y);
      b[i] += (float) (y * gain * Math.exp(-t / decay));
    }
  }

  /**
   * Adds a sine gliding exponentially from {@code fromHz} to {@code toHz} over its audible life,
   * decaying from {@code start}; {@code bright} adds a third harmonic for a reedier voice.
   */
  private static void tone(float[] b, double start, double fromHz, double toHz, double decay,
      float gain, boolean bright) {
    double life = decay * 4;
    double phase = 0;
    for (int i = (int) (start * SAMPLE_RATE); i < b.length; i++) {
      double t = i / SAMPLE_RATE - start;
      double hz = fromHz * Math.pow(toHz / fromHz, Math.min(1, t / life));
      phase += 2 * Math.PI * hz / SAMPLE_RATE;
      double s = Math.sin(phase) + (bright ? 0.3 * Math.sin(3 * phase) : 0);
      b[i] += (float) (s * gain * Math.exp(-t / decay));
    }
  }

  /** Saturates rather than clamps, so the loudest layered sounds round off instead of crackling. */
  private static short[] toPcm(float[] b) {
    int fade = (int) (TAIL_FADE * SAMPLE_RATE);
    short[] pcm = new short[b.length];
    for (int i = 0; i < b.length; i++) {
      float tail = Math.min(1f, (b.length - i) / (float) fade);
      pcm[i] = (short) Math.round(Math.tanh(b[i] * tail) * Short.MAX_VALUE);
    }
    return pcm;
  }
}
