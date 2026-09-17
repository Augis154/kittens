package kittens.common.net;

import java.util.Locale;

/** What an {@link EntityState} is; the client maps it to a sprite and a drawing routine. */
public enum EntityKind {
  CAT,
  RAT,
  MOUSE,
  BULLET,
  BOOM,
  HEALTH,
  AMMO;

  public boolean isEnemy() {
    return this == RAT || this == MOUSE;
  }

  public boolean isPickup() {
    return this == HEALTH || this == AMMO;
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
