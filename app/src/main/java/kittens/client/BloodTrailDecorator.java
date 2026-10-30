package kittens.client;

import java.awt.Graphics2D;
import java.util.function.Consumer;
import kittens.common.GameConfig;
import kittens.common.math.Vec2;

/** Final wound layer: more tiny body marks and footprints emitted while moving. */
final class BloodTrailDecorator extends CatDecorator {
  private static final float STEP = 8f;
  private Vec2 last;

  BloodTrailDecorator(CatRenderer wrapped) {
    super(wrapped);
  }

  @Override
  public void draw(Graphics2D g, Pose pose) {
    super.draw(g, pose);
    pixel(g, pose, 13, 22, Theme.BLOOD_DARK, 0.72f);
    pixel(g, pose, 14, 23, Theme.BLOOD, 0.46f);
    pixel(g, pose, 18, 21, Theme.BLOOD_DARK, 0.78f);
    pixel(g, pose, 19, 22, Theme.BLOOD, 0.42f);
    pixel(g, pose, 20, 25, Theme.BLOOD_DARK, 0.72f);
    pixel(g, pose, 19, 26, Theme.BLOOD, 0.40f);
  }

  @Override
  public void moved(Vec2 at, Consumer<Vec2> leaveTrail) {
    super.moved(at, leaveTrail);
    if (last == null) {
      last = at;
      return;
    }
    float distance = at.distance(last);
    if (distance < STEP) {
      return;
    }
    // Respawns and large corrections are teleports, not a path across the arena.
    if (distance <= GameConfig.TILE * 1.5f) {
      int count = (int) (distance / STEP);
      for (int i = 1; i <= count; i++) {
        leaveTrail.accept(last.add(at.sub(last).scale(i / (float) count)));
      }
    }
    last = at;
  }
}
