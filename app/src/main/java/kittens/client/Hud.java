package kittens.client;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import kittens.common.GameConfig;
import kittens.common.weapon.Weapon;

/**
 * The screen-space overlay: hearts, weapon bar, ammo, crosshair, and the connecting/downed screens.
 *
 * <p>Drawn at native window resolution rather than in world coordinates, so HUD text is crisp
 * instead of being magnified by {@code RENDER_SCALE} along with the arena. Everything it needs
 * arrives in one {@link View} snapshot; it reads no network or input state of its own.
 */
final class Hud {
  /** Number of heart icons the local player's health is split across. */
  private static final int HEART_COUNT = 5;
  private static final int HEART_SIZE = 30;
  private static final int HEART_GAP = 34;

  private static final int MARGIN = 16;
  private static final int SLOT_W = 88;
  private static final int SLOT_H = 60;
  private static final int SLOT_GAP = 8;

  /** Health fraction below which the screen edges start bleeding red. */
  private static final float LOW_HEALTH = 0.35f;

  /** Everything the HUD draws, gathered once per frame by {@link Client}. */
  record View(
      int playerId,
      float hp,
      Weapon weapon,
      int ammo,
      float reload,
      int enemyCount,
      boolean connected,
      boolean downed,
      int cursorX,
      int cursorY,
      float recoilBloom) {}

  private final AssetManager assets;

  Hud(AssetManager assets) {
    this.assets = assets;
  }

  void draw(Graphics2D g, int width, int height, View v) {
    Theme.quality(g);

    if (!v.connected()) {
      drawCurtain(g, width, height, "CONNECTING…", "waiting for the server", Theme.ACCENT);
      return;
    }

    float frac = Math.clamp(v.hp() / (float) GameConfig.PLAYER_MAX_HEALTH, 0f, 1f);
    // Full-screen states go under the panels, so health and ammo stay readable while downed.
    if (v.downed()) {
      vignette(g, width, height, Theme.alpha(Theme.DANGER, 0.45f));
      drawCurtain(g, width, height, "DOWNED", "respawning at your spawn point…", Theme.DANGER);
    } else if (frac < LOW_HEALTH) {
      // Pulse harder the closer to death, so peripheral vision carries the warning.
      float severity = 1f - frac / LOW_HEALTH;
      float pulse = (float) (0.65 + 0.35 * Math.sin(System.nanoTime() / 1e9 * 6.0));
      vignette(g, width, height, Theme.alpha(Theme.DANGER, 0.30f * severity * pulse));
    }

    drawHearts(g, v, frac);
    drawStatus(g, width, v);
    drawWeaponBar(g, width, height, v);
    drawAmmo(g, width, height, v);
    drawControls(g, height);

    if (!v.downed()) {
      drawCrosshair(g, v);
    }
  }

  // ---- health ---------------------------------------------------------------

  private void drawHearts(Graphics2D g, View v, float frac) {
    int panelW = (HEART_COUNT - 1) * HEART_GAP + HEART_SIZE + 24;
    int panelH = HEART_SIZE + 30;
    Theme.panel(g, MARGIN, MARGIN, panelW, panelH);

    int x0 = MARGIN + 12;
    int y0 = MARGIN + 10;
    float perHeart = (float) GameConfig.PLAYER_MAX_HEALTH / HEART_COUNT;
    BufferedImage heart = assets.image("utils/heart.png");
    Composite baseComposite = g.getComposite();
    Shape baseClip = g.getClip();
    for (int i = 0; i < HEART_COUNT; i++) {
      int hx = x0 + i * HEART_GAP;
      float fill = Math.clamp((v.hp() - i * perHeart) / perHeart, 0f, 1f);

      // Empty slot: a faint ghost of the heart, so the maximum is always legible.
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.16f));
      g.drawImage(heart, hx, y0, HEART_SIZE, HEART_SIZE, null);
      g.setComposite(baseComposite);

      // Filled portion: the full heart, clipped horizontally to the fraction remaining.
      if (fill > 0f) {
        g.setClip(hx, y0, Math.max(1, Math.round(HEART_SIZE * fill)), HEART_SIZE);
        g.drawImage(heart, hx, y0, HEART_SIZE, HEART_SIZE, null);
        g.setClip(baseClip);
      }
    }

    g.setFont(Theme.FONT_SMALL);
    String hp = Math.round(Math.ceil(v.hp())) + " / " + (int) GameConfig.PLAYER_MAX_HEALTH;
    Theme.shadowedText(g, hp, x0, y0 + HEART_SIZE + 15, Theme.healthColor(frac));
  }

  // ---- status pill ----------------------------------------------------------

  private void drawStatus(Graphics2D g, int width, View v) {
    g.setFont(Theme.FONT_LABEL);
    String tag = "P" + v.playerId();
    String enemies = v.enemyCount() + " hostile" + (v.enemyCount() == 1 ? "" : "s");
    int w = Math.max(Theme.textWidth(g, Theme.FONT_LABEL, tag),
            Theme.textWidth(g, Theme.FONT_SMALL, enemies)) + 28;
    int x = width - MARGIN - w;
    Theme.panel(g, x, MARGIN, w, 52);

    // Kitten swatch, so a player can tell at a glance which sprite is theirs.
    BufferedImage me = assets.kitten(GameConfig.kittenSprite(v.playerId()));
    g.drawImage(me, x + 8, MARGIN + 6, 20, 20, null);
    Theme.shadowedText(g, tag, x + 34, MARGIN + 22, Theme.TEXT);

    g.setFont(Theme.FONT_SMALL);
    Color mood = v.enemyCount() == 0 ? Theme.TEXT_DIM : Theme.WARN;
    Theme.shadowedText(g, enemies, x + 12, MARGIN + 42, mood);
  }

  // ---- weapon bar -----------------------------------------------------------

  private void drawWeaponBar(Graphics2D g, int width, int height, View v) {
    int count = Weapon.count();
    int totalW = count * SLOT_W + (count - 1) * SLOT_GAP;
    int x0 = (width - totalW) / 2;
    int y = height - MARGIN - SLOT_H;

    for (int i = 0; i < count; i++) {
      Weapon w = Weapon.byId(i);
      boolean active = w == v.weapon();
      int x = x0 + i * (SLOT_W + SLOT_GAP);
      // The selected slot lifts, brightens, and takes an accent border.
      int top = active ? y - 6 : y;
      int h = active ? SLOT_H + 6 : SLOT_H;

      Theme.panel(g, x, top, SLOT_W, h, active ? new Color(22, 32, 42, 205) : Theme.PANEL);
      if (active) {
        Stroke saved = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        g.setColor(Theme.ACCENT);
        g.drawRoundRect(x, top, SLOT_W - 1, h - 1, Theme.PANEL_RADIUS, Theme.PANEL_RADIUS);
        g.setStroke(saved);
      }

      int icon = 34;
      Composite baseComposite = g.getComposite();
      if (!active) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
      }
      g.drawImage(assets.weapon(w.sprite()), x + (SLOT_W - icon) / 2, top + 6, icon, icon, null);
      g.setComposite(baseComposite);

      g.setFont(Theme.FONT_SMALL);
      String hotkey = String.valueOf(w.id() + 1);
      Theme.shadowedText(g, hotkey, x + 7, top + 16, active ? Theme.ACCENT : Theme.TEXT_FAINT);

      String name = w.displayName();
      int nameX = x + (SLOT_W - Theme.textWidth(g, Theme.FONT_SMALL, name)) / 2;
      Theme.shadowedText(g, name, nameX, top + h - 9, active ? Theme.TEXT : Theme.TEXT_DIM);
    }
  }

  // ---- ammo -----------------------------------------------------------------

  private void drawAmmo(Graphics2D g, int width, int height, View v) {
    int mag = v.weapon().magazineSize();
    int panelW = 176;
    int panelH = 62;
    int x = width - MARGIN - panelW;
    int y = height - MARGIN - panelH;
    Theme.panel(g, x, y, panelW, panelH);

    boolean reloading = v.reload() > 0f;
    // Clamp for display: the server's weapon can lag a fresh number-key press by a tick.
    int ammo = Math.clamp(v.ammo(), 0, mag);
    float frac = reloading ? Math.clamp(v.reload(), 0f, 1f) : ammo / (float) mag;
    Color tint = reloading
        ? Theme.WARN
        : ammo == 0 ? Theme.DANGER : ammo <= Math.max(1, mag / 4) ? Theme.WARN : Theme.TEXT;

    g.setFont(Theme.FONT_DISPLAY);
    String big = String.valueOf(ammo);
    Theme.shadowedText(g, big, x + 14, y + 34, tint);

    g.setFont(Theme.FONT_SMALL);
    int bigW = Theme.textWidth(g, Theme.FONT_DISPLAY, big);
    Theme.shadowedText(g, "/ " + mag, x + 20 + bigW, y + 34, Theme.TEXT_DIM);
    String label = reloading ? "RELOADING" : "ROUNDS";
    int labelX = x + panelW - 12 - Theme.textWidth(g, Theme.FONT_SMALL, label);
    Theme.shadowedText(g, label, labelX, y + 22, reloading ? Theme.WARN : Theme.TEXT_FAINT);

    // Magazine gauge: one segment per round while that stays readable, else a plain bar.
    int barX = x + 14;
    int barY = y + panelH - 18;
    int barW = panelW - 28;
    int barH = 6;
    g.setColor(new Color(255, 255, 255, 34));
    g.fillRoundRect(barX, barY, barW, barH, barH, barH);
    if (reloading || mag > 12) {
      g.setColor(tint);
      g.fillRoundRect(barX, barY, Math.max(2, Math.round(barW * frac)), barH, barH, barH);
    } else {
      int segGap = 3;
      int segW = (barW - (mag - 1) * segGap) / mag;
      for (int i = 0; i < ammo; i++) {
        g.setColor(tint);
        g.fillRoundRect(barX + i * (segW + segGap), barY, segW, barH, barH, barH);
      }
    }
  }

  private void drawControls(Graphics2D g, int height) {
    g.setFont(Theme.FONT_SMALL);
    Theme.shadowedText(
        g,
        "WASD move · mouse aim · click fire · 1-4 weapon · R reload",
        MARGIN,
        height - MARGIN - 4,
        Theme.TEXT_FAINT);
  }

  // ---- crosshair ------------------------------------------------------------

  /**
   * Replaces the system cursor (hidden by {@link Client}). The gap tracks the weapon's spread cone
   * and blooms while firing, so the reticle itself communicates accuracy.
   */
  private void drawCrosshair(Graphics2D g, View v) {
    int cx = v.cursorX();
    int cy = v.cursorY();
    boolean reloading = v.reload() > 0f;
    Color color = reloading ? Theme.WARN : v.ammo() == 0 ? Theme.DANGER : Theme.ACCENT;

    float spreadPx = v.weapon().spread() * 90f;
    int gap = Math.round(5 + spreadPx + v.recoilBloom() * 10f);
    int len = 7;

    Stroke saved = g.getStroke();
    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(new Color(0, 0, 0, 110));
    ticks(g, cx, cy, gap + 1, len);
    g.setColor(color);
    ticks(g, cx, cy, gap, len);
    g.setStroke(saved);

    g.setColor(Theme.alpha(color, 0.9f));
    g.fillOval(cx - 1, cy - 1, 3, 3);

    if (reloading) {
      // A ring that closes as the magazine fills: reload progress where the eyes already are.
      g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      int r = gap + len + 6;
      g.setColor(new Color(0, 0, 0, 110));
      g.drawOval(cx - r, cy - r, r * 2, r * 2);
      g.setColor(Theme.WARN);
      g.drawArc(cx - r, cy - r, r * 2, r * 2, 90, -Math.round(360 * Math.clamp(v.reload(), 0f, 1f)));
      g.setStroke(saved);
    }
  }

  private static void ticks(Graphics2D g, int cx, int cy, int gap, int len) {
    g.drawLine(cx - gap - len, cy, cx - gap, cy);
    g.drawLine(cx + gap, cy, cx + gap + len, cy);
    g.drawLine(cx, cy - gap - len, cx, cy - gap);
    g.drawLine(cx, cy + gap, cx, cy + gap + len);
  }

  // ---- full-screen states ---------------------------------------------------

  /** Soft coloured bleed inward from all four screen edges. */
  private static void vignette(Graphics2D g, int width, int height, Color edge) {
    Color clear = Theme.alpha(edge, 0f);
    int depth = Math.round(height * 0.28f);
    g.setPaint(new GradientPaint(0, 0, edge, 0, depth, clear));
    g.fillRect(0, 0, width, depth);
    g.setPaint(new GradientPaint(0, height, edge, 0, height - depth, clear));
    g.fillRect(0, height - depth, width, depth);
    g.setPaint(new GradientPaint(0, 0, edge, depth, 0, clear));
    g.fillRect(0, 0, depth, height);
    g.setPaint(new GradientPaint(width, 0, edge, width - depth, 0, clear));
    g.fillRect(width - depth, 0, depth, height);
  }

  private static void drawCurtain(
      Graphics2D g, int width, int height, String title, String subtitle, Color tint) {
    g.setColor(new Color(6, 7, 10, 105));
    g.fillRect(0, 0, width, height);

    Font titleFont = Theme.FONT_DISPLAY.deriveFont(46f);
    g.setFont(titleFont);
    int tw = Theme.textWidth(g, titleFont, title);
    Theme.shadowedText(g, title, (width - tw) / 2, height / 2, tint);

    g.setFont(Theme.FONT_LABEL);
    int sw = Theme.textWidth(g, Theme.FONT_LABEL, subtitle);
    Theme.shadowedText(g, subtitle, (width - sw) / 2, height / 2 + 28, Theme.TEXT_DIM);
  }
}
