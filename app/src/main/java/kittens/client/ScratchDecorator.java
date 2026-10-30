package kittens.client;

import java.awt.Color;
import java.awt.Graphics2D;

final class ScratchDecorator extends CatDecorator {

  ScratchDecorator(CatRenderer wrapped) {
    super(wrapped);
  }

  @Override
  public void draw(Graphics2D g, Pose pose) {
    super.draw(g, pose);

    Color red = new Color(255, 30, 30);
    Color lightRed = new Color(255, 90, 90);

    // GALVA – 2 didesni ryškūs įbrėžimai
    // 1 įbrėžimas
    pixel(g, pose, 18, 9,  red, 1.0f);
    pixel(g, pose, 19, 10, red, 1.0f);
    pixel(g, pose, 20, 11, red, 1.0f);

    pixel(g, pose, 19, 9,  lightRed, 1.0f);
    pixel(g, pose, 20, 10, lightRed, 1.0f);
    pixel(g, pose, 21, 11, lightRed, 1.0f);

    // 2 įbrėžimas
    pixel(g, pose, 17, 12, red, 1.0f);
    pixel(g, pose, 18, 13, red, 1.0f);
    pixel(g, pose, 19, 14, red, 1.0f);

    pixel(g, pose, 18, 12, lightRed, 1.0f);
    pixel(g, pose, 19, 13, lightRed, 1.0f);
    pixel(g, pose, 20, 14, lightRed, 1.0f);

    // LETENA / RANKA – 2 įbrėžimai
    // 1 įbrėžimas
    pixel(g, pose, 6, 20, red, 1.0f);
    pixel(g, pose, 7, 21, red, 1.0f);
    pixel(g, pose, 8, 22, red, 1.0f);

    pixel(g, pose, 6, 21, lightRed, 1.0f);
    pixel(g, pose, 7, 22, lightRed, 1.0f);
    pixel(g, pose, 8, 23, lightRed, 1.0f);

    // 2 įbrėžimas
    pixel(g, pose, 6, 23, red, 1.0f);
    pixel(g, pose, 7, 24, red, 1.0f);
    pixel(g, pose, 8, 25, red, 1.0f);

    pixel(g, pose, 7, 23, lightRed, 1.0f);
    pixel(g, pose, 8, 24, lightRed, 1.0f);
    pixel(g, pose, 9, 25, lightRed, 1.0f);
  }
}