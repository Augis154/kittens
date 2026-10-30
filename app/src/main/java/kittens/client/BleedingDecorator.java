
package kittens.client;

import java.awt.Color;
import java.awt.Graphics2D;

final class BleedingDecorator extends CatDecorator {

  private static final Color WHITE = new Color(255, 255, 245);
  private static final Color CREAM = new Color(238, 230, 210);
  private static final Color GRAY = new Color(145, 145, 155);
  private static final Color BLOOD = new Color(255, 25, 35);
  private static final Color DARK_BLOOD = new Color(155, 10, 20);

  BleedingDecorator(CatRenderer wrapped) {
    super(wrapped);
  }

  @Override
  public void draw(Graphics2D g, Pose pose) {
    super.draw(g, pose);

    // 1. DIDELIS BINTAS ANT KAKTOS
    for (int y = 6; y <= 9; y++) {
      for (int x = 12; x <= 20; x++) {
        pixel(g, pose, x, y,
                y == 9 ? CREAM : WHITE, 1.0f);
      }
    }

    // Pilkos audinio raukšlės
    for (int y = 6; y <= 9; y++) {
      pixel(g, pose, 15, y, GRAY, 1.0f);
    }

    // Kraujas ant kaktos binto
    pixel(g, pose, 18, 7, BLOOD, 1.0f);
    pixel(g, pose, 19, 7, BLOOD, 1.0f);
    pixel(g, pose, 19, 8, DARK_BLOOD, 1.0f);

    // 2. DIDESNIS BINTAS ANT KRŪTINĖS
    for (int y = 18; y <= 25; y++) {
      int center = 15 + (y - 18) / 2;

      for (int x = center - 2; x <= center + 2; x++) {
        pixel(g, pose, x, y, WHITE, 1.0f);
      }
    }

    // Audinio raukšlės
    pixel(g, pose, 14, 19, GRAY, 1.0f);
    pixel(g, pose, 15, 20, GRAY, 1.0f);
    pixel(g, pose, 17, 22, GRAY, 1.0f);
    pixel(g, pose, 18, 24, GRAY, 1.0f);

    // Kraujas ant krūtinės
    pixel(g, pose, 17, 21, BLOOD, 1.0f);
    pixel(g, pose, 18, 21, BLOOD, 1.0f);
    pixel(g, pose, 18, 22, DARK_BLOOD, 1.0f);

    // 3. BINTAS ANT UODEGOS
    // Uodega yra kairėje sprite apačioje.
    for (int y = 21; y <= 25; y++) {
      for (int x = 6; x <= 8; x++) {
        pixel(g, pose, x, y, WHITE, 1.0f);
      }
    }

    // Tvarsčio juostelės
    pixel(g, pose, 6, 22, GRAY, 1.0f);
    pixel(g, pose, 7, 23, GRAY, 1.0f);
    pixel(g, pose, 8, 24, GRAY, 1.0f);

    // Maža kraujo dėmė ant uodegos
    pixel(g, pose, 7, 24, BLOOD, 1.0f);
    pixel(g, pose, 8, 25, DARK_BLOOD, 1.0f);
  }
}
