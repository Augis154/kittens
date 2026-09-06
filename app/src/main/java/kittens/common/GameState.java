package kittens.common;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

public class GameState implements Serializable {
  public ConcurrentHashMap<Integer, Player> players = new ConcurrentHashMap<>();

  public static class Player implements Serializable {
    public int x, y;

    public Player(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }
}
