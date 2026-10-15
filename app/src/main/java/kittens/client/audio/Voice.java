package kittens.client.audio;

/**
 * One playing instance of a {@link Sound}: a read head over samples shared with every other voice
 * of that sound, plus its own gain and pan. Mixer thread only.
 */
final class Voice {
  private final short[] samples;
  private final float left;
  private final float right;
  private int position;

  /** {@code pan} runs from -1 (hard left) to 1 (hard right). */
  Voice(short[] samples, float volume, float pan) {
    this.samples = samples;
    // Constant-power pan, so a sound crossing the screen doesn't dip in the middle.
    double theta = (Math.clamp(pan, -1f, 1f) + 1) * Math.PI / 4;
    this.left = (float) (volume * Math.cos(theta));
    this.right = (float) (volume * Math.sin(theta));
  }

  /** Adds the next {@code frames} into an interleaved stereo accumulator; false once played out. */
  boolean mixInto(float[] stereo, int frames) {
    int n = Math.min(frames, samples.length - position);
    for (int i = 0; i < n; i++) {
      float s = samples[position + i];
      stereo[2 * i] += s * left;
      stereo[2 * i + 1] += s * right;
    }
    position += n;
    return position < samples.length;
  }
}
