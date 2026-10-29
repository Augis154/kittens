package kittens.client;

import kittens.client.audio.Sound;
import kittens.client.audio.SoundSystem;
import kittens.client.events.GameEvent;
import kittens.client.events.GameObserver;
import kittens.common.math.Vec2;

/** The audible half of the client's reaction to game events (an Observer). */
final class SoundObserver implements GameObserver {
  /** What to play; {@code at} is null for a sound with no place in the world. */
  record Cue(Sound sound, Vec2 at) {}

  private final SoundSystem sounds;

  SoundObserver(SoundSystem sounds) {
    this.sounds = sounds;
  }

  /** Pure, so the event-to-sound table can be checked without opening an audio device. */
  static Cue cueFor(GameEvent event) {
    return switch (event) {
      case GameEvent.ShotFired s -> new Cue(Sound.shotFor(s.slot()), s.at());
      case GameEvent.EnemyHit h -> new Cue(Sound.HIT, h.at());
      case GameEvent.EnemyKilled k -> new Cue(Sound.SQUEAK, k.at());
      case GameEvent.PickupCollected p -> new Cue(p.health() ? Sound.HEAL : Sound.AMMO, p.at());
      case GameEvent.Exploded x -> new Cue(Sound.BOOM, x.at());
      case GameEvent.PlayerHurt h -> new Cue(Sound.HURT, null);
      case GameEvent.ReloadStarted r -> new Cue(Sound.RELOAD, null);
    };
  }

  @Override
  public void onEvent(GameEvent event) {
    Cue cue = cueFor(event);
    if (cue.at() == null) {
      sounds.play(cue.sound());
    } else {
      sounds.playAt(cue.sound(), cue.at());
    }
  }
}
