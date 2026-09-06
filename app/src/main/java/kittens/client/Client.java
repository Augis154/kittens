package kittens.client;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import kittens.common.GameState;

public class Client extends JPanel {
  private GameState currentState = new GameState();

  private Socket socket;
  private ObjectOutputStream out;

  public Client() throws IOException {
    socket = new Socket("localhost", 7000);
    out = new ObjectOutputStream(socket.getOutputStream());
    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

    // Thread to receive updates from server
    new Thread(() -> {
      try {
        while (true) {
          currentState = (GameState) in.readObject();
          repaint();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }).start();

    // Keyboard Input
    setFocusable(true);
    addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        try {
          if (e.getKeyCode() == KeyEvent.VK_W)
            out.writeObject("UP");
          if (e.getKeyCode() == KeyEvent.VK_S)
            out.writeObject("DOWN");
          if (e.getKeyCode() == KeyEvent.VK_A)
            out.writeObject("LEFT");
          if (e.getKeyCode() == KeyEvent.VK_D)
            out.writeObject("RIGHT");
          out.flush();
        } catch (IOException ex) {
          ex.printStackTrace();
        }
      }
    });
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    g.setColor(Color.BLACK);
    g.fillRect(0, 0, getWidth(), getHeight());

    g.setColor(Color.GREEN);
    currentState.players.forEach((id, pos) -> {
      g.fillRect(pos.x, pos.y, 30, 30);
      g.drawString("Player " + id, pos.x, pos.y - 5);
    });
  }

  public static void main(String[] args) throws IOException {
    JFrame frame = new JFrame("Kittens");
    frame.add(new Client());
    frame.setSize(800, 600);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setVisible(true);
  }
}
