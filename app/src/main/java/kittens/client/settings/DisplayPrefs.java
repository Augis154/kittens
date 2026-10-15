package kittens.client.settings;

import java.util.Map;
import java.util.Properties;

/**
 * Window scale and frame rate. The window is sized once at launch, so a new scale applies on the next
 * launch; a new frame rate applies when the game is next shown.
 *
 * @param renderScale on-screen pixels per world pixel
 * @param fps render frames per second; input is still sent at the server's tick rate
 */
public record DisplayPrefs(float renderScale, int fps) {
  static DisplayPrefs from(Properties p) {
    // Below 1.25 the arsenal menu's cards no longer fit the window.
    return new DisplayPrefs((float) SettingsStore.number(p, "display.scale", 1.75, 1.25, 3),
        (int) SettingsStore.number(p, "display.fps", 180, 30, 240));
  }

  void writeTo(Map<String, String> out) {
    out.put("display.scale", Float.toString(renderScale));
    out.put("display.fps", Integer.toString(fps));
  }
}
