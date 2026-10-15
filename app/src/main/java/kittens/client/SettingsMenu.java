package kittens.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.JPanel;
import kittens.client.audio.Sound;
import kittens.client.audio.SoundSystem;
import kittens.client.settings.Action;
import kittens.client.settings.AudioPrefs;
import kittens.client.settings.DisplayPrefs;
import kittens.client.settings.KeyBindings;
import kittens.client.settings.Profile;
import kittens.client.settings.Settings;
import kittens.common.GameConfig;

/**
 * The settings screen, opened with S from the arsenal menu and Esc from the game. Every change goes
 * straight to {@link Settings}, which saves it, so leaving needs no "apply" step. The arrows, Enter
 * and Esc that drive this screen are fixed rather than bindable, so a bad binding can always be
 * undone from here. EDT only.
 */
final class SettingsMenu extends JPanel {
  private static final int COL_W = 380;
  private static final int COL_GAP = 56;
  private static final int ROW_H = 32;
  private static final int SECTION_H = 30;
  private static final int TOP = 132;
  /** Kept clear at the bottom for the notice and the key hints. */
  private static final int FOOTER_ROOM = 70;
  private static final int ARROW_W = 14;
  private static final int ICON = 24;
  private static final float[] SCALES = {1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f};
  private static final int[] FRAME_RATES = {60, 90, 120, 144, 165, 180, 240};

  /**
   * One line of the menu. A non-null {@code section} starts a new group above it (blank for a gap
   * with no heading). Rows with a {@code step} are changed with left/right; {@code activate} is what
   * Enter or a click does. {@code icon}, when it yields an image, is drawn beside the value.
   */
  private record Row(int column, String section, String label, Supplier<String> value, String note,
      IntConsumer step, Runnable activate, Supplier<BufferedImage> icon) {
    Row(int column, String section, String label, Supplier<String> value, String note,
        IntConsumer step, Runnable activate) {
      this(column, section, label, value, note, step, activate, () -> null);
    }
  }

  private enum Mode { BROWSE, NAME, KEY }

  private final Settings settings = Settings.get();
  private final AssetManager assets;
  private final Runnable onBack;
  private final List<Row> rows = new ArrayList<>();
  private final Rectangle[] bounds;
  /** Screen x of each adjustable row's left arrow, for clicks; set while painting. */
  private final int[] leftArrowX;
  private int selected;
  private Mode mode = Mode.BROWSE;
  private Action capturing;
  private final StringBuilder draft = new StringBuilder();
  private String notice;

  SettingsMenu(Dimension size, AssetManager assets, boolean inGame, Runnable onBack) {
    this.assets = assets;
    this.onBack = onBack;
    buildRows(inGame);
    bounds = new Rectangle[rows.size()];
    leftArrowX = new int[rows.size()];
    setPreferredSize(size);
    setBackground(Theme.FLOOR);
    setFocusable(true);
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        onKey(e.getKeyCode());
        repaint();
      }

      @Override
      public void keyTyped(KeyEvent e) {
        onTyped(e.getKeyChar());
        repaint();
      }
    });
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int hit = rowAt(e.getX(), e.getY());
        if (mode == Mode.BROWSE && hit >= 0 && hit != selected) {
          selected = hit;
          repaint();
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        onClick(e.getX(), e.getY());
        repaint();
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  private void buildRows(boolean inGame) {
    AudioPrefs audio = settings.audio();
    KeyBindings keys = settings.keys();
    Profile profile = settings.profile();
    rows.add(new Row(0, "PLAYER", "Name",
        () -> mode == Mode.NAME ? draft + "|" : profile.name(), null, null, this::editName));
    List<String> colors = Profile.colors();
    rows.add(new Row(0, null, "Kitten", () -> colorTitle(profile.color()), null,
        dir -> profile.setColor(colors.get(
            Math.clamp(colors.indexOf(profile.color()) + dir, 0, colors.size() - 1))),
        null,
        () -> Profile.AUTO.equals(profile.color()) ? null : assets.kitten(profile.color())));
    rows.add(new Row(0, "AUDIO", "Volume", () -> Math.round(audio.volume() * 100) + "%", null,
        dir -> {
          if (dir > 0) {
            audio.louder();
          } else {
            audio.quieter();
          }
          sample();
        }, null));
    rows.add(new Row(0, null, "Sound", () -> audio.muted() ? "Off" : "On", null,
        dir -> toggleSound(), this::toggleSound));
    rows.add(new Row(0, "DISPLAY", "Window scale",
        () -> trim(settings.display().renderScale()) + "×", "next launch",
        dir -> settings.setDisplay(new DisplayPrefs(
            step(SCALES, settings.display().renderScale(), dir), settings.display().fps())), null));
    rows.add(new Row(0, null, "Frame rate", () -> settings.display().fps() + " fps", null,
        dir -> settings.setDisplay(new DisplayPrefs(settings.display().renderScale(),
            step(FRAME_RATES, settings.display().fps(), dir))), null));
    rows.add(new Row(0, "", "Back", () -> inGame ? "to the game" : "to the arsenals", null, null,
        onBack));

    boolean first = true;
    for (Action a : Action.values()) {
      rows.add(new Row(1, first ? "CONTROLS" : null, a.title,
          () -> capturing == a ? "press a key…" : keys.labels(a), null, null, () -> capture(a)));
      first = false;
    }
    rows.add(new Row(1, "", "Reset controls", () -> "defaults", null, null, () -> {
      keys.resetToDefaults();
      notice = "Controls reset to their defaults.";
    }));
  }

  // ---- input ----

  private void onKey(int code) {
    switch (mode) {
      case KEY -> captureKey(code);
      case NAME -> {
        if (code == KeyEvent.VK_ENTER) {
          settings.profile().setName(draft.toString());
          mode = Mode.BROWSE;
        } else if (code == KeyEvent.VK_ESCAPE) {
          mode = Mode.BROWSE;
        } else if (code == KeyEvent.VK_BACK_SPACE && !draft.isEmpty()) {
          draft.setLength(draft.length() - 1);
        }
      }
      case BROWSE -> {
        switch (code) {
          case KeyEvent.VK_UP -> select(selected - 1);
          case KeyEvent.VK_DOWN -> select(selected + 1);
          case KeyEvent.VK_LEFT -> change(-1);
          case KeyEvent.VK_RIGHT -> change(1);
          case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> activate();
          case KeyEvent.VK_ESCAPE -> onBack.run();
          default -> { }
        }
      }
    }
  }

  /** Name characters arrive as typed characters, so the keyboard layout is respected. */
  private void onTyped(char c) {
    boolean allowed = Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-';
    if (mode == Mode.NAME && allowed && draft.length() < GameConfig.MAX_NAME_LENGTH) {
      draft.append(c);
    }
  }

  private void onClick(int x, int y) {
    if (mode == Mode.NAME) {
      settings.profile().setName(draft.toString()); // clicking away keeps what was typed
      mode = Mode.BROWSE;
    } else if (mode == Mode.KEY) {
      cancelCapture();
      return;
    }
    int hit = rowAt(x, y);
    if (hit < 0) {
      return;
    }
    selected = hit;
    Row row = rows.get(hit);
    if (row.step() != null && x >= leftArrowX[hit] - 8) {
      row.step().accept(x <= leftArrowX[hit] + ARROW_W + 8 ? -1 : 1);
      notice = null;
    } else {
      activate();
    }
  }

  private void select(int index) {
    selected = Math.floorMod(index, rows.size());
  }

  private void change(int dir) {
    Row row = rows.get(selected);
    if (row.step() != null) {
      notice = null;
      row.step().accept(dir);
    }
  }

  private void activate() {
    Row row = rows.get(selected);
    notice = null;
    if (row.activate() != null) {
      row.activate().run();
    } else if (row.step() != null) {
      row.step().accept(1);
    }
  }

  private void editName() {
    draft.setLength(0);
    draft.append(settings.profile().name());
    mode = Mode.NAME;
  }

  private void capture(Action action) {
    capturing = action;
    mode = Mode.KEY;
  }

  private void captureKey(int code) {
    if (code == KeyEvent.VK_ESCAPE) {
      cancelCapture();
    } else if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
      notice = "Number keys pick weapons, so they can't be bound. Press another key.";
    } else {
      settings.keys().rebind(capturing, code);
      notice = capturing.title + " is now " + settings.keys().labels(capturing) + ".";
      cancelCapture();
    }
  }

  private void cancelCapture() {
    capturing = null;
    mode = Mode.BROWSE;
  }

  private void toggleSound() {
    if (!settings.audio().toggleMuted()) {
      sample();
    }
  }

  /** A click at the new level, so the player hears what they picked. */
  private static void sample() {
    SoundSystem.get().play(Sound.SWITCH);
  }

  private int rowAt(int x, int y) {
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] != null && bounds[i].contains(x, y)) {
        return i;
      }
    }
    return -1;
  }

  // ---- painting ----

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics;
    Theme.quality(g);

    // Centred as a block, so a large window doesn't leave the menu stranded at the top.
    int[] height = {0, 0};
    for (Row row : rows) {
      height[row.column()] += (row.section() != null ? SECTION_H : 0) + ROW_H;
    }
    int dy = Math.max(0, (getHeight() - FOOTER_ROOM - TOP - Math.max(height[0], height[1])) / 2);

    centered(g, "SETTINGS", Theme.FONT_DISPLAY, dy + 72, Theme.TEXT);
    centered(g, "Saved as you change them  ·  " + settings.location(), Theme.FONT_SMALL, dy + 98,
        Theme.TEXT_FAINT);

    int left = (getWidth() - (2 * COL_W + COL_GAP)) / 2;
    int[] y = {dy + TOP, dy + TOP};
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      int col = row.column();
      int x = left + col * (COL_W + COL_GAP);
      if (row.section() != null) {
        y[col] += SECTION_H;
        g.setFont(Theme.FONT_SMALL);
        Theme.shadowedText(g, row.section(), x, y[col] - 10, Theme.ACCENT);
      }
      bounds[i] = new Rectangle(x - 10, y[col] - 4, COL_W + 20, ROW_H - 2);
      drawRow(g, i, row, x, y[col] + 18);
      y[col] += ROW_H;
    }

    int footerY = getHeight() - 30;
    if (notice != null) {
      centered(g, notice, Theme.FONT_LABEL, footerY - 28, Theme.WARN);
    }
    centered(g, switch (mode) {
      case BROWSE -> "Up/Down choose   ·   Left/Right change   ·   Enter edit or rebind   ·   Esc back";
      case NAME -> "Type a name   ·   Enter saves   ·   Esc cancels";
      case KEY -> "Press the new key for " + capturing.title + "   ·   Esc cancels";
    }, Theme.FONT_SMALL, footerY, Theme.TEXT_FAINT);
  }

  private void drawRow(Graphics2D g, int i, Row row, int x, int baseline) {
    boolean active = i == selected;
    boolean editing = active && mode != Mode.BROWSE;
    Rectangle r = bounds[i];
    if (active) {
      Theme.panel(g, r.x, r.y, r.width, r.height, new Color(22, 32, 42, 205));
      Stroke saved = g.getStroke();
      g.setStroke(new BasicStroke(editing ? 2f : 1f));
      g.setColor(editing ? Theme.ACCENT : Theme.ACCENT_DIM);
      g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 8, 8);
      g.setStroke(saved);
    }

    g.setFont(Theme.FONT_LABEL);
    Theme.shadowedText(g, row.label(), x, baseline, active ? Theme.TEXT : Theme.TEXT_DIM);
    if (row.note() != null) {
      int lw = Theme.textWidth(g, Theme.FONT_LABEL, row.label());
      g.setFont(Theme.FONT_SMALL);
      Theme.shadowedText(g, row.note(), x + lw + 10, baseline, Theme.TEXT_FAINT);
    }

    String value = row.value().get();
    Font font = Theme.FONT_LABEL;
    int right = x + COL_W;
    Color valueColor = editing ? Theme.ACCENT : active ? Theme.TEXT : Theme.TEXT_DIM;
    g.setFont(font);
    BufferedImage icon = row.icon().get();
    if (row.step() != null) {
      int vw = Theme.textWidth(g, font, value);
      Color arrow = active ? Theme.ACCENT : Theme.TEXT_FAINT;
      Theme.shadowedText(g, "›", right - ARROW_W + 4, baseline, arrow);
      int valueX = right - ARROW_W - 10 - vw;
      Theme.shadowedText(g, value, valueX, baseline, valueColor);
      if (icon != null) {
        valueX -= ICON + 8;
        g.drawImage(icon, valueX, baseline - ICON + 6, ICON, ICON, null);
      }
      leftArrowX[i] = valueX - 10 - ARROW_W;
      Theme.shadowedText(g, "‹", leftArrowX[i] + 4, baseline, arrow);
    } else {
      Theme.shadowedText(g, value, right - Theme.textWidth(g, font, value), baseline, valueColor);
    }
  }

  private void centered(Graphics2D g, String text, Font font, int y, Color color) {
    g.setFont(font);
    Theme.shadowedText(g, text, (getWidth() - Theme.textWidth(g, font, text)) / 2, y, color);
  }

  // ---- option stepping ----

  /** The option {@code dir} places from whichever option is nearest {@code current}, clamped. */
  private static float step(float[] options, float current, int dir) {
    int nearest = 0;
    for (int i = 1; i < options.length; i++) {
      if (Math.abs(options[i] - current) < Math.abs(options[nearest] - current)) {
        nearest = i;
      }
    }
    return options[Math.clamp(nearest + dir, 0, options.length - 1)];
  }

  private static int step(int[] options, int current, int dir) {
    float[] asFloat = new float[options.length];
    for (int i = 0; i < options.length; i++) {
      asFloat[i] = options[i];
    }
    return Math.round(step(asFloat, current, dir));
  }

  /** "Auto" explains itself in the menu; colours are capitalised sprite names. */
  private static String colorTitle(String color) {
    return Profile.AUTO.equals(color) ? "Auto (by join order)"
        : Character.toUpperCase(color.charAt(0)) + color.substring(1);
  }

  /** "1.5", "2", "1.75": no trailing zeros. */
  private static String trim(float scale) {
    return scale == Math.round(scale) ? Integer.toString(Math.round(scale))
        : Float.toString(scale).replaceAll("0+$", "");
  }
}
