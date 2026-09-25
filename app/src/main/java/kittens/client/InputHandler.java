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

  private final Set<Integer> held = new HashSet<>();
  private final Map<Integer, Timer> pendingRelease = new HashMap<>();
  private float moveX;
  private float moveY;
  private boolean firing;
  private boolean reloadRequested;
  private int weaponId; // role 0, the sidearm
  private int mouseX;
  private int mouseY;

  InputHandler(JComponent target, int mouseX, int mouseY) {
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

  /** Wire id of the selected role; the authoritative selection is the server's. */
  int weaponId() {
    return weaponId;
  }

  Weapon weapon() {
    return WeaponFactory.weapon(weaponId);
  }

  int mouseX() {
    return mouseX;
  }

  int mouseY() {
    return mouseY;
  }

  /** One-shot: true once per R press, then cleared. */
  boolean consumeReload() {
    boolean r = reloadRequested;
    reloadRequested = false;
    return r;
  }

  private void onKeyPressed(int code) {
    if (code >= KeyEvent.VK_1 && code < KeyEvent.VK_1 + WeaponFactory.count()) {
      weaponId = code - KeyEvent.VK_1;
      return;
    }
    if (code == KeyEvent.VK_R) {
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

  private boolean down(int... codes) {
    for (int c : codes) {
      if (held.contains(c)) {
        return true;
      }
    }
    return false;
  }

  private void recomputeDirection() {
    moveX = (down(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1f : 0f)
        - (down(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1f : 0f);
    moveY = (down(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1f : 0f)
        - (down(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1f : 0f);
  }
}
