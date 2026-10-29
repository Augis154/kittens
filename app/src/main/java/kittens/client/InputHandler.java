package kittens.client;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.Timer;
import kittens.client.audio.AudioControls;
import kittens.client.audio.Sound;
import kittens.client.audio.SoundSystem;
import kittens.client.settings.Action;
import kittens.client.settings.KeyBindings;
import kittens.client.settings.Settings;
import kittens.common.weapon.Weapon;
import kittens.common.weapon.WeaponFactory;

/**
 * The local player's raw intent, kept current by Swing listeners and sampled on each input tick.
 * EDT only. Stores the cursor in screen pixels — a cached world position would go stale as the
 * camera pans.
 */
final class InputHandler {
  /**
   * X11 autorepeat emits release+press pairs while a key is held; a release is deferred this long
   * and cancelled if the matching press arrives, so movement doesn't stutter.
   */
  private static final int RELEASE_GRACE_MS = 45;

  private final KeyBindings keys = Settings.get().keys();
  private final Set<Integer> held = new HashSet<>();
  private final Map<Integer, Timer> pendingRelease = new HashMap<>();
  private float moveX;
  private float moveY;
  private boolean firing;
  private boolean reloadRequested;
  private final WeaponFactory family;
  private int slot; // 0, the sidearm
  private int mouseX;
  private int mouseY;

  InputHandler(JComponent target, int mouseX, int mouseY, WeaponFactory family) {
    this.family = family;
    this.mouseX = mouseX;
    this.mouseY = mouseY;
    target.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        onKeyPressed(e.getKeyCode());
      }

      @Override
      public void keyReleased(KeyEvent e) {
        onKeyReleased(e.getKeyCode());
      }
    });
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          firing = true;
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          firing = false;
        }
      }
    };
    target.addMouseListener(mouse);
    target.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        trackCursor(e);
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        trackCursor(e);
      }
    });
  }

  float moveX() {
    return moveX;
  }

  float moveY() {
    return moveY;
  }

  boolean firing() {
    return firing;
  }

  /** Position in the chosen arsenal; the authoritative selection is the server's. */
  int slot() {
    return slot;
  }

  Weapon weapon() {
    return family.weaponAt(slot);
  }

  /** Draws the weapon in {@code chosen}, with the switch click if it changed. */
  void select(int chosen) {
    if (slot != chosen) {
      slot = chosen;
      SoundSystem.get().play(Sound.SWITCH);
    }
  }

  int mouseX() {
    return mouseX;
  }

  int mouseY() {
    return mouseY;
  }

  /** Drops every held key and button, so nothing stays pressed while another screen has the keyboard. */
  void release() {
    pendingRelease.values().forEach(Timer::stop);
    pendingRelease.clear();
    held.clear();
    firing = false;
    reloadRequested = false;
    recomputeDirection();
  }

  /** One-shot: true once per reload press, then cleared. */
  boolean consumeReload() {
    boolean r = reloadRequested;
    reloadRequested = false;
    return r;
  }

  private void onKeyPressed(int code) {
    if (code >= KeyEvent.VK_1 && code < KeyEvent.VK_1 + family.size()) {
      select(code - KeyEvent.VK_1);
      return;
    }
    if (keys.is(Action.MUTE, code)) {
      AudioControls.toggleMute();
      return;
    }
    if (keys.is(Action.VOLUME_UP, code)) {
      AudioControls.louder();
      return;
    }
    if (keys.is(Action.VOLUME_DOWN, code)) {
      AudioControls.quieter();
      return;
    }
    if (keys.is(Action.RELOAD, code)) {
      reloadRequested = true;
      return;
    }
    Timer grace = pendingRelease.remove(code);
    if (grace != null) {
      grace.stop();
    }
    if (held.add(code)) {
      recomputeDirection();
    }
  }

  private void onKeyReleased(int code) {
    Timer grace = new Timer(RELEASE_GRACE_MS, ev -> {
      pendingRelease.remove(code);
      if (held.remove(code)) {
        recomputeDirection();
      }
    });
    grace.setRepeats(false);
    pendingRelease.put(code, grace);
    grace.start();
  }

  private void trackCursor(MouseEvent e) {
    mouseX = e.getX();
    mouseY = e.getY();
  }

  private boolean down(Action action) {
    for (int c : held) {
      if (keys.is(action, c)) {
        return true;
      }
    }
    return false;
  }

  private void recomputeDirection() {
    moveX = (down(Action.MOVE_RIGHT) ? 1f : 0f) - (down(Action.MOVE_LEFT) ? 1f : 0f);
    moveY = (down(Action.MOVE_DOWN) ? 1f : 0f) - (down(Action.MOVE_UP) ? 1f : 0f);
  }
}
