package kittens.common.entity;

import java.util.Collection;
import kittens.common.map.TileMap;
import kittens.common.math.Vec2;
import kittens.common.sim.PathField;

/** Chooses an enemy's desired movement direction; {@link Enemy} applies the actual movement. */
public interface MovementStrategy {
  Vec2 direction(Enemy enemy, TileMap map, PathField pursuit,
      Collection<? extends Actor> targets, double dt);
}
