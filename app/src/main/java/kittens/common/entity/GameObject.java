package kittens.common.entity;

import kittens.common.math.Aabb;
import kittens.common.math.Vec2;

/** Base type for everything that lives in the game world. No rendering or networking here. */
public abstract class GameObject {
  protected final int id;
  protected Vec2 pos;
  protected Vec2 size;
  protected boolean alive = true;

  protected GameObject(int id, Vec2 pos, Vec2 size) {
    this.id = id;
    this.pos = pos;
    this.size = size;
  }

  /** Advance this object by {@code dt} seconds. */
  public abstract void update(double dt);

  public Aabb bounds() {
    return Aabb.fromCenter(pos, size);
  }

  public void kill() {
    alive = false;
  }

  public int id() {
    return id;
  }

  public Vec2 pos() {
    return pos;
  }

  public void setPos(Vec2 pos) {
    this.pos = pos;
  }

  public Vec2 size() {
    return size;
  }

  public void setSize(Vec2 size) {
    this.size = size;
  }

  public boolean isAlive() {
    return alive;
  }
}
