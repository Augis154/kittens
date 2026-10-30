
package kittens.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kittens.common.math.Vec2;
import kittens.common.net.EntityKind;
import kittens.common.net.EntityState;

/**
 * Manages cat appearance decorators and temporary blood marks.
 */
final class CatAppearance {

  // Shorter blood trail: marks disappear after 0.7 seconds.
  private static final float TRAIL_LIFE = 0.7f;

  private static final Color BLOOD_RED = new Color(255, 20, 30);
  private static final Color BLOOD_DARK = new Color(145, 0, 12);
  private static final Color BLOOD_SHADOW = new Color(190, 0, 15);

  private static final class Mark {
    final Vec2 at;
    final int shape;
    float life = TRAIL_LIFE;

    Mark(Vec2 at, int shape) {
      this.at = at;
      this.shape = shape;
    }
  }

  private final CatRenderer basic = new BasicCatRenderer();

  private final Map<Integer, CatRenderer> renderers = new HashMap<>();
  private final Map<Integer, Integer> tiers = new HashMap<>();
  private final List<Mark> marks = new ArrayList<>();

  private int nextMarkShape;

  void update(double dt, Map<Integer, EntityState> entities,
              int myPlayerId, WorldView view, Vec2 predicted) {

    // Fade existing blood marks.
    for (Mark mark : marks) {
      mark.life -= (float) dt;
    }

    marks.removeIf(mark -> mark.life <= 0f);

    tiers.keySet().removeIf(id -> !entities.containsKey(id));
    renderers.keySet().removeIf(id -> !entities.containsKey(id));

    for (EntityState entity : entities.values()) {
      if (entity.kind() != EntityKind.CAT) {
        continue;
      }

      int tier = tier(entity.hp());

      // Rebuild decorators only when HP tier changes.
      if (tiers.getOrDefault(entity.id(), -1) != tier) {
        tiers.put(entity.id(), tier);
        renderers.put(entity.id(), chain(tier));
      }

      // Only the third tier leaves blood marks.
      if (tier == 3) {
        Vec2 at = entity.id() == myPlayerId && predicted != null
                ? predicted
                : view.positionOf(entity);

        renderers.get(entity.id()).moved(
                at,
                pos -> marks.add(
                        new Mark(pos, nextMarkShape++ % 5)
                )
        );
      }
    }
  }

  CatRenderer forPlayer(int id) {
    return renderers.getOrDefault(id, basic);
  }

  // Draw temporary blood marks on the floor.
  void drawTrail(Graphics2D g) {
    for (Mark mark : marks) {

      float alpha = Math.max(0f,
              Math.min(1f, mark.life / TRAIL_LIFE));

      int x = Math.round(mark.at.x);
      int y = Math.round(mark.at.y + 12f);

      // Dark blood shape.
      g.setColor(Theme.alpha(BLOOD_DARK, 0.9f * alpha));

      switch (mark.shape) {

        case 0 -> {
          // Large irregular mark.
          g.fillRect(x - 3, y, 6, 2);
          g.fillRect(x - 2, y - 2, 3, 2);
          g.fillRect(x, y + 2, 3, 1);
          g.fillRect(x + 4, y + 1, 1, 1);
        }

        case 1 -> {
          // Small scattered drops.
          g.fillRect(x - 2, y, 2, 2);
          g.fillRect(x + 2, y - 2, 1, 1);
          g.fillRect(x + 3, y + 2, 1, 1);
        }

        case 2 -> {
          // Long narrow mark.
          g.fillRect(x - 4, y, 7, 2);
          g.fillRect(x - 2, y - 1, 3, 1);
          g.fillRect(x + 1, y + 2, 2, 1);
        }

        case 3 -> {
          // Medium asymmetric mark.
          g.fillRect(x - 2, y - 1, 4, 3);
          g.fillRect(x + 2, y, 2, 1);
          g.fillRect(x - 3, y + 2, 1, 1);
        }

        default -> {
          // Tiny blood drops.
          g.fillRect(x, y, 2, 2);
          g.fillRect(x - 2, y + 1, 1, 1);
        }
      }

      // Bright centre, contrasting with dark edges.
      g.setColor(Theme.alpha(BLOOD_RED, 0.95f * alpha));
      g.fillRect(x, y, 2, 1);
    }
  }

  /**
   * Draw a vivid red blood shadow underneath a critically injured cat.
   *
   * x and y must be the centre of the desired ground shadow
   * in the same coordinate system as Graphics2D.
   */
  void drawBloodShadow(Graphics2D g, int playerId,
                       int x, int y) {

    if (tiers.getOrDefault(playerId, 0) != 3) {
      return;
    }

    // Outer blood pool: roughly the size of the blue orbit.
    g.setColor(Theme.alpha(BLOOD_SHADOW, 0.85f));
    g.fillOval(x - 16, y - 6, 32, 12);

    // Darker inner pool.
    g.setColor(Theme.alpha(BLOOD_DARK, 0.80f));
    g.fillOval(x - 13, y - 4, 26, 8);

    // Bright red centre.
    g.setColor(Theme.alpha(BLOOD_RED, 0.75f));
    g.fillOval(x - 9, y - 3, 18, 6);

    // Small irregular pixel-art edges.
    g.setColor(Theme.alpha(BLOOD_RED, 0.80f));
    g.fillRect(x - 16, y, 3, 2);
    g.fillRect(x + 13, y + 1, 3, 2);
    g.fillRect(x - 8, y + 5, 4, 2);
    g.fillRect(x + 5, y - 6, 3, 2);
  }

  private static int tier(float hp) {
    if (hp <= 0f || hp > 60f) {
      return 0;
    }

    if (hp > 40f) {
      return 1;
    }

    return hp > 20f ? 2 : 3;
  }

  private CatRenderer chain(int tier) {
    return switch (tier) {
      case 1 -> new ScratchDecorator(
              new BasicCatRenderer()
      );

      case 2 -> new BleedingDecorator(
              new ScratchDecorator(
                      new BasicCatRenderer()
              )
      );

      case 3 -> new BloodTrailDecorator(
              new BleedingDecorator(
                      new ScratchDecorator(
                              new BasicCatRenderer()
                      )
              )
      );

      default -> basic;
    };
  }
}
