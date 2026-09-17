package kittens.common.entity;

import kittens.common.math.Aabb;
import kittens.common.math.Vec2;

/** Base of everything in the world: an id, a centre, a box and a liveness flag. */
public abstract class GameObject {
  protected final int id;
  protected final Vec2 size;
  protected Vec2 pos;
  protected boolean alive = true;

  protected GameObject(int id, Vec2 pos, Vec2 size) {
    this.id = id;
    this.pos = pos;
    this.size = size;
  }

  public int id() {
    return id;
  }

  public Vec2 pos() {
    return pos;
  }

  public Vec2 size() {
    return size;
  }

  public Aabb bounds() {
    return Aabb.fromCenter(pos, size);
  }

  public boolean isAlive() {
    return alive;
  }

  public void kill() {
    alive = false;
  }
}
