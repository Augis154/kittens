package kittens.common.net;

import java.util.Locale;

/** What an {@link EntityState} is; the client maps it to a sprite and a drawing routine. */
public enum EntityKind {
  CAT,
  RAT,
  MOUSE,
  BULLET,
  BOOM,
  /** A shootable supply crate; its contents are deliberately not on the wire. */
  CRATE,
  HEALTH,
  AMMO,
  DOUBLE_DAMAGE,
  SHIELD,
  CATNIP,
  NINE_LIVES;

  public boolean isEnemy() {
    return this == RAT || this == MOUSE;
  }

  /** An item lying on the floor, waiting to be walked over. A {@link #CRATE} is not one until it breaks. */
  public boolean isPickup() {
    return switch (this) {
      case HEALTH, AMMO, DOUBLE_DAMAGE, SHIELD, CATNIP, NINE_LIVES -> true;
      default -> false;
    };
  }

  /** Actors travel between snapshots, so the client eases their position. */
  public boolean isActor() {
    return this == CAT || isEnemy();
  }

  /** Lower-case name, used as the sprite file name. */
  public String sprite() {
    return name().toLowerCase(Locale.ROOT);
  }
}
