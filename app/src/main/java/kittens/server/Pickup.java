package kittens.server;

import kittens.common.GameConfig;
import kittens.common.entity.GameObject;
import kittens.common.math.Vec2;
import kittens.common.net.EntityState;

/**
 * A collectable lying on the floor: a medkit that heals, or a crate that tops every magazine back
 * up. Sits still, expires after {@link GameConfig#PICKUP_LIFETIME} seconds, and is consumed by the
 * first player who walks into it <em>and would benefit</em> — a kitten at full health walks over a
 * medkit rather than wasting it. {@link PickupDirector} decides where and when they appear.
 *
 * <p>On the wire it rides an {@link EntityState} of kind {@code "health"} or {@code "ammo"}, with
 * {@code hp} carrying the seconds of life it has left so the client can blink it out as it ages.
 */
final class Pickup extends GameObject {
  /** What a pickup restores. The tag is the wire {@code kind} the client maps to an icon. */
  enum Kind {
    HEALTH("health"),
    AMMO("ammo");

    private final String tag;

    Kind(String tag) {
      this.tag = tag;
    }

    String tag() {
      return tag;
    }
  }

  private static final Vec2 SIZE = Vec2.of(GameConfig.PICKUP_SIZE, GameConfig.PICKUP_SIZE);

  private final Kind kind;
  private double life = GameConfig.PICKUP_LIFETIME;

  Pickup(int id, Kind kind, Vec2 pos) {
    super(id, pos, SIZE);
    this.kind = kind;
  }

  /** Ages the pickup; it dies of old age here and is culled by {@code GameWorld} this same tick. */
  @Override
  public void update(double dt) {
    life -= dt;
    if (life <= 0) {
      kill();
    }
  }

  /**
   * Hands this pickup to {@code player} if they are touching it and it would actually do
   * something, returning whether it was consumed. A consumed pickup is dead immediately, so no two
   * players can share one.
   */
  boolean tryCollect(ServerPlayer player) {
    if (!isAlive() || player.dead() || !bounds().intersects(player.bounds())) {
      return false;
    }
    switch (kind) {
      case HEALTH -> {
        if (!player.wantsHealth()) {
          return false;
        }
        player.heal(GameConfig.PICKUP_HEALTH_AMOUNT);
      }
      case AMMO -> {
        if (!player.wantsAmmo()) {
          return false;
        }
        player.restockAmmo();
      }
    }
    kill();
    return true;
  }

  EntityState toEntityState() {
    return new EntityState(
        id, kind.tag(), pos.x, pos.y, 0f, (float) Math.max(0.0, life), -1, 0f);
  }
}
