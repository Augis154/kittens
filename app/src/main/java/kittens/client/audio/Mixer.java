package kittens.client.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Blends every playing {@link Voice} into the one output line, on its own {@code audio-mixer}
 * thread. Mixing in software rather than opening a {@code Clip} per shot is what keeps a rifle
 * burst from exhausting the device's lines and stuttering.
 */
final class Mixer implements Runnable {
  /** 16-bit stereo, since every {@link Voice} is panned. */
  private static final AudioFormat FORMAT =
      new AudioFormat(SoundBank.SAMPLE_RATE, 16, 2, true, false);
  /** About 12 ms per write: the latency floor between an event and hearing it. */
  private static final int CHUNK_FRAMES = 256;
  /** Device buffer, in chunks: enough to ride out a scheduling hiccup without adding much delay. */
  private static final int BUFFERED_CHUNKS = 4;
  /** Headroom so several loud voices at once saturate less. */
  private static final float MASTER_GAIN = 0.7f;
  /** Past this many, the oldest voice is dropped — it is also the most decayed. */
  private static final int MAX_VOICES = 24;

  private final SourceDataLine line;
  private final Queue<Voice> incoming = new ConcurrentLinkedQueue<>();
  private final List<Voice> playing = new ArrayList<>(); // mixer thread only

  private Mixer(SourceDataLine line) {
    this.line = line;
  }

  /** Opens the device and starts mixing; throws if there is no device to open. */
  static Mixer open() throws LineUnavailableException {
    SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
    line.open(FORMAT, CHUNK_FRAMES * FORMAT.getFrameSize() * BUFFERED_CHUNKS);
    line.start();
    Mixer mixer = new Mixer(line);
    Thread thread = new Thread(mixer, "audio-mixer");
    thread.setDaemon(true);
    thread.start();
    return mixer;
  }

  /** Any thread. */
  void add(Voice voice) {
    incoming.add(voice);
  }

  @Override
  public void run() {
    float[] mix = new float[CHUNK_FRAMES * 2];
    byte[] out = new byte[CHUNK_FRAMES * FORMAT.getFrameSize()];
    while (true) {
      for (Voice v; (v = incoming.poll()) != null; ) {
        if (playing.size() >= MAX_VOICES) {
          playing.remove(0);
        }
        playing.add(v);
      }
      Arrays.fill(mix, 0f);
      playing.removeIf(v -> !v.mixInto(mix, CHUNK_FRAMES));
      for (int i = 0; i < mix.length; i++) {
        int s = Math.clamp(Math.round(mix[i] * MASTER_GAIN), Short.MIN_VALUE, Short.MAX_VALUE);
        out[2 * i] = (byte) s;
        out[2 * i + 1] = (byte) (s >> 8);
      }
      // Blocks until the device has room, which is what paces this loop. Silence is written too,
      // so the line never underruns and latency stays constant.
      line.write(out, 0, out.length);
    }
  }
}
