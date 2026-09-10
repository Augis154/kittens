package kittens.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The client's single visual vocabulary: palette, fonts, and the few drawing primitives the HUD and
 * the world renderer both use. Purely presentational — nothing here affects simulation, so it is
 * safe to retune freely (unlike {@link kittens.common.GameConfig}, which both sides must agree on).
 */
final class Theme {
  private Theme() {}

  // ---- world palette --------------------------------------------------------

  static final Color FLOOR = new Color(27, 28, 35);
  /** Alternating floor shade, applied on a tile checkerboard so the grid reads without lines. */
  static final Color FLOOR_ALT = new Color(36, 37, 47);
  static final Color WALL = new Color(78, 83, 104);
  /** Lit cap on wall tiles with open floor above them. */
  static final Color WALL_TOP = new Color(116, 123, 151);
  /** Dark seam where a wall meets the floor, and along its exposed sides. */
  static final Color WALL_EDGE = new Color(38, 40, 52);
  static final Color SPAWN_TILE = new Color(32, 48, 42);
  static final Color SPAWN_MARK = new Color(72, 128, 104, 120);
  /** Cast down from a wall onto the floor tile below it, faking a little height. */
  static final Color WALL_SHADOW = new Color(0, 0, 0, 90);
  static final Color ACTOR_SHADOW = new Color(0, 0, 0, 70);

  // ---- accents --------------------------------------------------------------

  static final Color ACCENT = new Color(120, 210, 255);
  static final Color ACCENT_DIM = new Color(120, 210, 255, 70);
  static final Color WARN = new Color(245, 200, 110);
  static final Color DANGER = new Color(232, 96, 88);
  static final Color GOOD = new Color(126, 214, 138);
  static final Color TEXT = new Color(236, 240, 248);
  static final Color TEXT_DIM = new Color(236, 240, 248, 130);
  static final Color TEXT_FAINT = new Color(236, 240, 248, 80);

  // ---- panels ---------------------------------------------------------------

  static final Color PANEL = new Color(12, 13, 18, 165);
  static final Color PANEL_BORDER = new Color(255, 255, 255, 28);
  static final int PANEL_RADIUS = 12;

  // ---- fonts ----------------------------------------------------------------

  /** Big readouts (ammo count, overlay headlines). */
  static final Font FONT_DISPLAY = new Font(Font.SANS_SERIF, Font.BOLD, 30);
  static final Font FONT_LABEL = new Font(Font.SANS_SERIF, Font.BOLD, 15);
  static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.BOLD, 12);
  /** Nameplates and floating damage, drawn in world space so they stay small on screen. */
  static final Font FONT_WORLD = new Font(Font.SANS_SERIF, Font.BOLD, 10);

  // ---- primitives -----------------------------------------------------------

  static void quality(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
  }

  /** The standard translucent HUD plate: fill plus a one-pixel highlight border. */
  static void panel(Graphics2D g, int x, int y, int w, int h) {
    panel(g, x, y, w, h, PANEL);
  }

  static void panel(Graphics2D g, int x, int y, int w, int h, Color fill) {
    g.setColor(fill);
    g.fillRoundRect(x, y, w, h, PANEL_RADIUS, PANEL_RADIUS);
    g.setColor(PANEL_BORDER);
    g.drawRoundRect(x, y, w - 1, h - 1, PANEL_RADIUS, PANEL_RADIUS);
  }

  /** Text with a soft dark backing so it survives a light floor or a sprite underneath. */
  static void shadowedText(Graphics2D g, String text, int x, int y, Color color) {
    g.setColor(new Color(0, 0, 0, 150));
    g.drawString(text, x + 1, y + 1);
    g.setColor(color);
    g.drawString(text, x, y);
  }

  static int textWidth(Graphics2D g, Font font, String text) {
    return g.getFontMetrics(font).stringWidth(text);
  }

  /** Interpolates between two colours; {@code t} is clamped to [0, 1]. */
  static Color lerp(Color a, Color b, float t) {
    float k = Math.clamp(t, 0f, 1f);
    return new Color(
        Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
        Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
        Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k),
        Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * k));
  }

  static Color alpha(Color c, float a) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * Math.clamp(a, 0f, 1f)));
  }

  /** Health-bar colour: green down through amber to red as the fraction drops. */
  static Color healthColor(float frac) {
    return frac > 0.5f
        ? lerp(WARN, GOOD, (frac - 0.5f) * 2f)
        : lerp(DANGER, WARN, frac * 2f);
  }
}
