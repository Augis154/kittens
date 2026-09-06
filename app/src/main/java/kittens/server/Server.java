package kittens.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import kittens.common.GameState;

public class Server {
  private static final int PORT = 7000;

  private static GameState state = new GameState();
  private static List<ObjectOutputStream> clients = new ArrayList<>();

  public static void main(String[] args) throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(PORT)) {
      System.out.println("Server started on port " + PORT);

      new Thread(() -> {
        while (true) {
          broadcastState();
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
          }
        }
      }).start();

      int idCounter = 0;
      while (true) {
        Socket socket = serverSocket.accept();
        int playerId = idCounter++;
        new Thread(() -> handleClient(socket, playerId)).start();
      }
    }
  }

  private static void handleClient(Socket socket, int id) {
    try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

      synchronized (clients) {
        clients.add(out);
      }

      state.players.put(id, new GameState.Player(100, 100));
      System.out.println("Player " + id + " joined.");

      while (true) {
        // Read movement command from client
        String command = (String) in.readObject();
        GameState.Player p = state.players.get(id);
        if ("UP".equals(command))
          p.y -= 5;
        if ("DOWN".equals(command))
          p.y += 5;
        if ("LEFT".equals(command))
          p.x -= 5;
        if ("RIGHT".equals(command))
          p.x += 5;
      }

    } catch (Exception e) {
      state.players.remove(id);
      System.out.println("Player " + id + " disconnected.");
    }
  }

  private static void broadcastState() {
    synchronized (clients) {
      Iterator<ObjectOutputStream> it = clients.iterator();
      while (it.hasNext()) {
        ObjectOutputStream out = it.next();
        try {
          out.reset(); // Important: Clears object cache
          out.writeObject(state);
        } catch (IOException e) {
          it.remove();
        }
      }
    }
  }
}
