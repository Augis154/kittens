package kittens.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;
import kittens.common.weapon.Weapon;
import kittens.common.weapon.WeaponFactory;

/**
 * The screen shown before the socket opens: one card per {@link WeaponFactory}, picked by number
 * key, arrows + Enter, or click. The {@link Chooser} does the connecting, which takes a while, so the
 * menu shows "Connecting…" and ignores further picks until the chooser calls its {@code fail}
 * callback (or the game replaces this panel). EDT only, including {@code fail}.
 */
final class ArsenalMenu extends JPanel {
  private static final int CARD_W = 400;
  private static final int CARD_H = 430;
  private static final int CARD_GAP = 48;
  private static final int ROW_H = 60;
  private static final int ICON = 44;

  private final List<WeaponFactory> families = WeaponFactory.families();
  private final AssetManager assets;
  private final Chooser onChoose;
  private int highlight;
  private String error;
  private boolean connecting;

  /** Starts joining with {@code family}; must call {@code fail} (on the EDT) if it cannot. */
  interface Chooser {
    void choose(WeaponFactory family, Consumer<String> fail);
  }

  ArsenalMenu(AssetManager assets, Dimension size, Chooser onChoose) {
    this.assets = assets;
    this.onChoose = onChoose;
    setPreferredSize(size);
    setBackground(Theme.FLOOR);
    setFocusable(true);
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        onKey(e.getKeyCode());
      }
    });
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int hit = cardAt(e.getX(), e.getY());
        if (hit >= 0 && hit != highlight) {
          highlight = hit;
          repaint();
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        int hit = cardAt(e.getX(), e.getY());
        if (hit >= 0) {
          choose(hit);
        }
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  private void onKey(int code) {
    int digit = code - KeyEvent.VK_1;
    if (digit >= 0 && digit < families.size()) {
      choose(digit);
    } else if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) {
      highlight = Math.floorMod(highlight - 1, families.size());
      repaint();
    } else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) {
      highlight = Math.floorMod(highlight + 1, families.size());
      repaint();
    } else if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) {
      choose(highlight);
    }
  }

  private void choose(int index) {
    if (connecting) {
      return;
    }
    highlight = index;
    connecting = true;
    error = null;
    repaint();
    onChoose.choose(families.get(index), message -> {
      connecting = false;
      error = message;
      repaint();
    });
  }

  private Rectangle cardBounds(int index) {
    int total = families.size() * CARD_W + (families.size() - 1) * CARD_GAP;
    int x0 = (getWidth() - total) / 2;
    int top = (getHeight() - CARD_H) / 2 + 30;
    return new Rectangle(x0 + index * (CARD_W + CARD_GAP), top, CARD_W, CARD_H);
  }

  private int cardAt(int x, int y) {
    for (int i = 0; i < families.size(); i++) {
      if (cardBounds(i).contains(x, y)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics;
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    Theme.quality(g);

    centered(g, "CHOOSE YOUR ARSENAL", Theme.FONT_DISPLAY, getHeight() / 2 - CARD_H / 2 - 20,
        Theme.TEXT);
    centered(g, "You keep it for the whole match.", Theme.FONT_LABEL,
        getHeight() / 2 - CARD_H / 2 + 6, Theme.TEXT_DIM);

    for (int i = 0; i < families.size(); i++) {
      drawCard(g, families.get(i), i, cardBounds(i), i == highlight);
    }

    int footerY = getHeight() - 36;
    if (connecting) {
      centered(g, "Connecting…", Theme.FONT_LABEL, footerY - 26, Theme.ACCENT);
    } else if (error != null) {
      centered(g, error, Theme.FONT_LABEL, footerY - 26, Theme.DANGER);
    }
    centered(g, "Press 1-" + families.size() + " or click   ·   ←/→ and Enter", Theme.FONT_SMALL,
        footerY, Theme.TEXT_FAINT);
  }

  private void drawCard(Graphics2D g, WeaponFactory family, int index, Rectangle r,
      boolean active) {
    Theme.panel(g, r.x, r.y, r.width, r.height,
        active ? new Color(22, 32, 42, 205) : Theme.PANEL);
    if (active) {
      Stroke saved = g.getStroke();
      g.setStroke(new BasicStroke(2f));
      g.setColor(Theme.ACCENT);
      g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, Theme.PANEL_RADIUS,
          Theme.PANEL_RADIUS);
      g.setStroke(saved);
    }

    g.setFont(Theme.FONT_SMALL);
    Theme.shadowedText(g, String.valueOf(index + 1), r.x + 16, r.y + 26,
        active ? Theme.ACCENT : Theme.TEXT_FAINT);
    centered(g, family.title(), Theme.FONT_DISPLAY, r.x + r.width / 2, r.y + 64,
        active ? Theme.TEXT : Theme.TEXT_DIM);

    g.setFont(Theme.FONT_SMALL);
    int lineY = r.y + 92;
    for (String line : wrap(g.getFontMetrics(), family.blurb(), r.width - 56)) {
      centered(g, line, Theme.FONT_SMALL, r.x + r.width / 2, lineY, Theme.TEXT_DIM);
      lineY += 16;
    }

    for (int slot = 0; slot < family.size(); slot++) {
      Weapon w = family.weaponAt(slot);
      int y = r.y + 150 + slot * ROW_H;
      g.drawImage(assets.weapon(w.sprite()), r.x + 28, y, ICON, ICON, null);
      g.setFont(Theme.FONT_LABEL);
      Theme.shadowedText(g, w.displayName(), r.x + 28 + ICON + 18, y + 20,
          active ? Theme.TEXT : Theme.TEXT_DIM);
      g.setFont(Theme.FONT_SMALL);
      Theme.shadowedText(g, stats(w), r.x + 28 + ICON + 18, y + 38, Theme.TEXT_FAINT);
    }
  }

  private static String stats(Weapon w) {
    StringBuilder sb = new StringBuilder(w.magazineSize() + (w.magazineSize() == 1 ? " round" : " rounds"));
    if (w.pellets() > 1) {
      sb.append("  ·  ").append(w.pellets()).append(" pellets");
    }
    if (w.explosive()) {
      sb.append("  ·  explosive");
    }
    return sb.toString();
  }

  private static List<String> wrap(FontMetrics fm, String text, int maxWidth) {
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : text.split(" ")) {
      if (line.length() > 0 && fm.stringWidth(line + " " + word) > maxWidth) {
        lines.add(line.toString());
        line.setLength(0);
      }
      line.append(line.length() == 0 ? "" : " ").append(word);
    }
    lines.add(line.toString());
    return lines;
  }

  private void centered(Graphics2D g, String text, java.awt.Font font, int y, Color color) {
    centered(g, text, font, getWidth() / 2, y, color);
  }

  private void centered(Graphics2D g, String text, java.awt.Font font, int cx, int y, Color color) {
    g.setFont(font);
    Theme.shadowedText(g, text, cx - Theme.textWidth(g, font, text) / 2, y, color);
  }
}
