# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A networked 2D top-down arena shooter in plain **Java 21 + Swing/AWT**. You play a kitten in a
sewer/alley arena shooting waves of rats and mice. Multiplayer is server-authoritative with
client-side prediction.

`.claude.md` (lowercase, not auto-loaded) holds the original design brief and is worth reading for
intent. Two constraints from it shape everything here:

1. **No game framework or engine.** Rendering, netcode, collision, and the game loop are all
   hand-written on purpose. Do not propose pulling in LibGDX, LWJGL, Netty, etc.
2. **23 OOP design patterns must eventually be implemented**, added gradually over months.
   **Do not refactor code into additional design patterns unless asked.** Keep the core free of
   rendering and socket code so later patterns have clean seams. Strategy now selects enemy movement
   in `Enemy`; other patterns already present include Singleton weapons shared by every `Arsenal`,
   Singleton `Server`, `SoundSystem` and `Settings`, Command in `InputCommand`, Facade in `GameWorld`
   and `AudioControls`, Observer in `GameEvents`, Game Loop in `ServerLoop`, Flyweight cache in
   `AssetManager`, Factory in `EnemyFactory`, Abstract Factory in `WeaponFactory`, and Builder in
   `TileMap`. Leave pattern labels accurate when touching those classes. Before/after class diagrams
   for the Abstract Factory, Observer and Facade additions are in `docs/`.

## Keeping this file current

This file is the context every agent and teammate loads, so treat it as part of the codebase: if a
change you make would leave anything below wrong or missing, fix it in the same turn rather than
leaving the next agent to rediscover it. A `Stop` hook (`.claude/hooks/claude-md-reminder.sh`) prints
a one-time reminder when sources changed and this file did not. It is a nudge, not a gate — it
cannot tell whether an update was actually warranted, so that judgement is yours.

**Update it when you:**

- change or add an invariant, a threading rule, or a subsystem's responsibility
- add or change a message type, an `EntityState` field, or an entity `kind`
- reorder the `GameWorld.tick` pipeline, or move work between threads
- add a package, or a file that other code is expected to route through
- fix or newly discover something in **Known rough edges** — delete what is fixed, add what is not
- lose real debugging time to a trap worth warning about (add it to the probe notes)
- begin the design-patterns work, which retires the standing "do not refactor" constraint

**Do not update it for** routine feature work that fits the existing structure, individual bug
fixes, tuning-value changes, or anything git history already records. This is a map, not a
changelog: no "recent changes" section, no dated entries, no per-file inventory.

**How:** edit the affected section in place instead of appending — rewrite what became wrong, delete
what became stale. Keep every claim checkable against the code, and prefer pointing at `GameConfig`
over restating numbers that will drift. Verify before you write: this file is only worth loading if
an agent can trust it without re-deriving it.

## Commands

```bash
./gradlew runServer     # start the authoritative server (port 7000); has stdin attached
./gradlew runClient     # start a client window; connects to localhost
./gradlew build         # compile + assemble
./gradlew compileJava   # fastest correctness check while iterating
```

The server and each client are **separate processes**. To test multiplayer, run one `runServer` and
two or more `runClient`. A client takes an optional host argument (`kittens.client.Client <host>`);
with no argument it uses `localhost`.

### There is no test suite

`src/test` does not exist, and `junit-jupiter` is declared in `gradle/libs.versions.toml` but never
wired into `app/build.gradle.kts` dependencies. `./gradlew test` therefore does nothing.

To verify simulation changes without a harness, compile a throwaway probe against the built classes.
Note that `javac` is **not on PATH** — use the JDK Gradle provisioned:

```bash
JDK=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin
$JDK/javac -cp app/build/classes/java/main -d /tmp/probe Probe.java
$JDK/java -cp "app/build/classes/java/main:app/src/main/resources:/tmp/probe" Probe
```

Put a probe in `package kittens.server` or `kittens.common.entity` when it needs package-private or
`protected` access (`Projectile`, `ServerPlayer`, `GameWorld` are all package-private). The map
resource must be on the classpath, hence `app/src/main/resources`.

When probing, be careful that the *probe* isn't what's wrong. Traps hit repeatedly in practice: a
plain `Actor` target has finite health and no respawn, so enemies kill it mid-measurement and every
later assertion silently reads a dead target; a freshly constructed `ServerPlayer` carries
`RESPAWN_INVULNERABILITY` of spawn grace, so `damage()` called straight after the constructor is
silently dropped and the probe goes on measuring a player at full health; firing "into open space"
usually means firing into a wall a few tiles away, so check the map before picking coordinates; and
enemies spawned already touching their target swing in lockstep, which flatters anything that
rate-limits damage; `SpawnDirector.tick` *returns* the enemy it spawned, so a probe that calls it and
drops the result still spends the wave budget; and players left standing in a `GameWorld` are killed
within seconds, which stops the director, so a full-world run never reaches the enemy cap — measure
that by ticking `Enemy` directly against immortal `Actor` targets instead.

To *see* the client instead of measuring it, don't try to screen-grab: under WSLg the X11 root
window is black, so `ffmpeg -f x11grab` captures nothing. Render the panel offscreen instead — build
a `Client` on the EDT, `setSize` it to its preferred size, and `paint()` it into a `BufferedImage`.
No window ever has to be shown. Such a probe needs `package kittens.client` for `GameClient`, and
Gson on the classpath — `find ~/.gradle/caches -name 'gson-*.jar'` finds the jar Gradle already
downloaded. The server can run in the same JVM (`Server.main` on a daemon thread), and
`GameClient.latest()` gives the probe the authoritative position to assert against.

Driving that panel has two non-obvious requirements. Add it to a `JFrame` and `pack()` it (still
never shown): `MouseEvent`'s constructor calls `getLocationOnScreen`, which needs a peer. And feed
keys **straight to `panel.getKeyListeners()`** rather than via `dispatchEvent` — AWT routes key
events through the `KeyboardFocusManager` to the focus owner, and an unshown frame has none, so they
are silently dropped and the kitten never moves while the rest of the frame looks fine.

## Architecture

Three packages under `app/src/main/java/kittens/`, with a hard rule: **`common` contains no
rendering, and the only socket code in it is `common/net/MessageChannel`** — the one place that
knows the wire is newline-delimited JSON over TCP, used by both ends. Everything else in `common`
is the shared simulation both sides run.

- **`common/`** — `math/` (`Vec2`, `Aabb`), `map/` (`TileMap` and its `Builder`, `Tile`,
  `LevelGenerator`), `entity/`
  (`GameObject` → `Actor` → `Enemy` → `Rat`/`Mouse`, `EnemyFactory`, `MovementStrategy`), `weapon/`
  (`Weapon`, `WeaponFactory`, `Arsenal`; each family in its own subpackage — `standard/`, `scavenger/`),
  `sim/` (`Motion`, `PathField`),
  `net/` (the DTOs, `EntityKind`, `MessageCodec`, `MessageChannel`), `GameConfig`.
- **`server/`** — `Server` (accept loop + per-client reader threads), `ServerLoop`, `GameWorld`,
  `ServerPlayer` (carries a `common.weapon.Arsenal`), `Projectile`, `Explosion`, `Pickup`,
  `SpawnDirector`, `PickupDirector`.
- **`client/`** — `GameClient` (networking; publishes each snapshot as an immutable `Frame`),
  `Client` (window, timers, scene assembly; its `main` shows `ArsenalMenu` first and only connects
  once a family is picked), `InputHandler` (keys, mouse, cursor), `Predictor`
  (local-player prediction and reconciliation), `Camera` (viewport, follow, world/screen
  conversion), `WorldView` (between-snapshot smoothing, and diffing snapshots into game events),
  `Effects` and `SoundObserver` (the two observers that turn those events into visuals and sound),
  `Renderer` (arena drawing), `Hud` (screen-space overlay), `Theme` (palette, fonts, panel/text
  primitives), `AssetManager`, and the two menus: `ArsenalMenu` (pick an arsenal, then join) and
  `SettingsMenu`.
- **`client/events/`** — the Observer machinery: `GameEvent` (a sealed family of records),
  `GameObserver` and `GameEvents` (the subject observers subscribe to).
- **`client/audio/`** — `SoundSystem` (the public entry point), `Sound`, `AudioControls` (a Facade
  over the audio setting and the output), and the package-private `SoundBank` (synthesis), `Mixer`
  (the output line) and `Voice` (one playing sound).
- **`client/settings/`** — `Settings` (the public entry point) and its sections `Profile`,
  `KeyBindings` (with the `Action` enum), `AudioPrefs` and `DisplayPrefs`; `SettingsStore` is the
  file.

### Levels

A level is only ever made by `TileMap.Builder` (Builder pattern): `fromText`/`fromResource` are the
text path and `LevelGenerator` the procedural one, and both route through the same builder. The
builder is the mutable phase and `TileMap` stays immutable after `build()`, which is the only place a
level is validated and the only place `spawnPoints()` and `floorPoints()` can be derived — a grid
assembled cell by cell has no parse loop to derive them in, and `floorPoints` is the pool
`PickupDirector` samples. Nothing outside `build()` may compute those lists.

`build()` now checks the rules this section used to warn were unchecked: rectangular, sealed by walls on
every edge, all open cells mutually reachable, and at least one spawn (`GameWorld.addPlayer` indexes
`spawnPoints` directly). Corridor width is deliberately *not* checked — a design preference is not a
property a grid can be tested for, so `LevelGenerator` guarantees the 2-wide paths (a rat's box is 22px
against a 32px tile) by construction instead. A generator that fails validation should be fixed by
drawing a new seed, not by a builder that silently repairs the level, which would hide which rule the
generator breaks.

`GameConfig.PROCEDURAL_LEVEL` picks which path: rooms placed with a seeded `Random` (never
`ThreadLocalRandom`, so a seed reproduces a level), chained into a ring by 2-wide corridors, one `S` per
room. `LEVEL_SEED` 0 means the clock. The server is the only place a level is chosen and it **sends the
assembled level over the wire** as glyph rows, so the client never runs the generator and never has to
agree on a seed; `TileMap.rows()` is the wire shape and `fromText` is its inverse. With procedural
generation off, `GameConfig.MAP_RESOURCE` picks the hand-authored map instead.

Plain `.` floor is also where pickups land: `floorPoints()` is every open cell that is *not* a spawn, so
marking a cell `S` takes it out of the pickup pool as well as adding a wave entrance.

`maps/sewers.txt` (60x36, four rooms, a central hub, a corridor ring) and `maps/arena.txt` (exactly one
viewport, kept for probes) both pass `build()`'s checks, and generated levels use the same 60x36
footprint so the camera still has somewhere to scroll.

### Authority and prediction

`ServerLoop` is a fixed-timestep accumulator on a daemon thread at `GameConfig.TICK_HZ` (30 Hz).
Each tick `GameWorld.tick(dt)` runs a fixed pipeline — players → weapon fire → pickups → spawn
director → enemies → projectiles → explosion detonation → cull — then `Server.tick` sends **one
snapshot per client**, because `ackSeq`, `viewerAmmo`, and `viewerReload` are all recipient-specific.
`wave` and `intermission` (seconds to the next wave, 0 while one runs) ride on every snapshot too but
are the same for everyone; they come from `SpawnDirector.wave()`/`intermission()` and reach the HUD
through `GameClient.Frame`. The countdown only advances while someone is alive, so it freezes while
every player is downed.

`Client` renders at the player's `DisplayPrefs.fps` (180 by default) but sends input at exactly
`TICK_HZ`. `Predictor` applies input to
the local player immediately, then on each new snapshot (detected by the `Frame`'s tick changing)
re-anchors to the authoritative position and replays inputs the server hasn't acked, easing small
errors and hard-snapping past a threshold.

Everything the local player does *not* control is smoothed by `WorldView` instead: remote players
and enemies are eased exponentially toward their snapshot positions, and bullets are dead-reckoned
from their angle and weapon speed. The split matters — prediction is reconciled against authority
and lives in `Predictor`, while `WorldView` is cosmetic and can be retuned without desync risk.
`WorldView` is also where the client notices that something happened — a new bullet, a health drop, a
vanished pickup — and it publishes each as a `GameEvent` instead of reacting itself.

### Events

`GameEvents` is the subject (Observer pattern) and `GameObserver` the interface. `Client` subscribes
two observers: `Effects` (muzzle flashes, hit sparks, floating damage numbers, pickup rings, the
white flash on a struck enemy) and `SoundObserver` (the event-to-sound table, `cueFor`). `WorldView`
publishes what it sees in snapshots; `Client` publishes the two events only it can see,
`PlayerHurt` and `ReloadStarted`, read off the local kitten's own snapshot.

- **A reaction to something that happened is a new observer, not a line in `WorldView`.** That class
  must stay free of `SoundSystem` and of effect state; that coupling is what the bus replaced.
- `GameEvent` is sealed, so an observer that switches over it without a `default` stops compiling when
  an event is added — `SoundObserver` does that on purpose, while `Effects` has a `default` because it
  only cares about three. A new event is a record, published from the one place that detects it, and
  handled by whichever observers care.
- Delivery is synchronous on the publishing thread, which is the Swing EDT. A throwing observer is
  logged and skipped so it cannot starve the others or stop the frame loop. Events are client-only:
  nothing here crosses the wire, and the server has no equivalent.
- `Effects.advance(dt)` runs after `WorldView.update` each frame, so an effect born this frame ages by
  this frame's `dt`.
- Feedback that is not a world event — the click on a weapon switch or a volume change — is not an
  event; it is played directly, or through `AudioControls`.

**The critical invariant:** `common/sim/Motion` is the *single* movement model. Its player overload
is run by both `ServerPlayer.tick` and `Predictor` with the same fixed `GameConfig.TICK_DT` step,
and must stay pure and deterministic. Any divergence — a different timestep, a stray random, an
extra force applied on one side only — surfaces as unexplained rubber-banding, not as an obvious
failure. Enemies use the same `Motion.step` with a blocker predicate that also refuses living
players; that path is server-only, so it is free to change.

### Wire protocol

Newline-delimited JSON over TCP (Gson), five message types only, modelled as a sealed interface plus
records: `Join`, `JoinAccepted`, `InputCommand`, `Snapshot`, `ProfileUpdate`. `MessageCodec` wraps
each in a `{"type":..,"data":..}` envelope so the DTOs stay plain data.

Who a player *is* travels apart from where they are. `Join` and `ProfileUpdate` carry a name and a
kitten colour; the server cleans the name (`GameConfig.cleanName`), resolves the colour
(`GameConfig.kittenColor`, where "auto" or anything unknown means join order), and every `Snapshot`
carries the result as a `players` roster of `PlayerInfo` rows. The roster is sent whole each tick
rather than on change: it is a few bytes per player, needs no catch-up for late joiners, and a rename
shows on the next tick. Clients draw names and colours only from the roster, never from the id.

`JoinAccepted` is the one message that carries state beyond ids: the level as glyph rows plus its tile
size, because the server is the only place a level is chosen. Widening an existing record was free —
Gson serializes by field and `MessageCodec`'s two switches need no change — which is why the level went
onto `JoinAccepted` rather than becoming a fifth message type.

`EntityState` is one flat render-ready row. Two conventions to know:

- `kind` is the `EntityKind` enum (`CAT`, `RAT`, `MOUSE`, `BULLET`, `BOOM`, `HEALTH`, `AMMO`),
  serialized by name. Its `isEnemy()`/`isPickup()`/`isActor()` are the only way code should ask
  "what sort of thing is this"; `sprite()` is the lower-case resource name.
- **`weaponId` is a wire id, not a slot.** It is unique across weapon families
  (`WeaponFactory.decode`), because a client must draw weapons and bullets belonging to players who
  chose a different arsenal than its own. `InputCommand.slot` is the other space: a position in the
  *sender's own* arsenal, resolved server-side, so a client can never select a weapon it does not
  carry. Don't pass one where the other is expected — both are plain ints.
- **Two kinds overload the fields** rather than widening the record for everyone: for `BOOM`,
  `angle` carries the current expanding radius and `hp` the final radius; for `HEALTH`/`AMMO`,
  `hp` carries the pickup's remaining lifetime in seconds. Both are read only for presentation. A
  third overload is the point at which this wants a better shape.

Adding a field to `EntityState` touches all five construction sites (`ServerPlayer`, `Enemy`,
`Projectile`, `Explosion`, `Pickup`). Records serialize by field, and both sides share the class, so
the wire stays consistent automatically.

### Threading

- **Server:** main thread accepts sockets; one reader thread per client (`client-<id>`) runs
  `MessageChannel.readLoop` and queues inputs into `ServerPlayer.inbox` (a
  `ConcurrentLinkedQueue`); the `server-loop` thread drains them and owns all mutation.
  `GameWorld.players` is the only concurrent collection, because `addPlayer`/`removePlayer` arrive
  from reader threads. The one field a reader thread writes on a live player is
  `ServerPlayer.info`, a volatile `PlayerInfo` replaced whole by `ProfileUpdate`; enemies, projectiles, explosions and pickups are plain lists touched by the
  loop thread only. Keep it that way — adding a concurrent collection there implies a threading
  rule that does not exist.
- **Snapshot writes happen on the loop thread** and end in a blocking `flush()`. A merely *slow*
  client (full TCP window, not disconnected) will stall the whole simulation. A per-connection
  outbound queue is the fix if this ever bites.
- **Client:** the `client-net` reader thread publishes each snapshot as one immutable
  `GameClient.Frame` through a single volatile, so a render frame never sees half a snapshot;
  everything else (input state, prediction, rendering) is Swing EDT only, driven by two `Timer`s.
  The level is the one other thing the reader thread publishes, once, through a second volatile and a
  `CountDownLatch` — `Client` cannot assemble a camera, renderer or predictor around a map it does not
  have, so `Client.join` waits for it on a `client-join` thread — never the EDT, or the menu would
  freeze for the wait — and builds the `Client` on the EDT once it arrives.
  Audio is the other exception to "EDT only": an `audio-mixer` daemon thread owns the output line, and
  `SoundSystem.play`/`playAt` hand it voices through a queue, so they are safe from any thread.

### Enemy AI

`Enemy.tick` delegates its movement direction to a per-enemy `MovementStrategy`. Rats spawn with
`PursuitMovementStrategy` and mice with `WeavingMovementStrategy`, which keeps its own phase so mice
weave out of sync. After the first melee attack that actually damages a player, the same enemy
reports that first successful bite to `GameWorld`, which assigns `HitAndRunMovementStrategy` (rat) or
`OrbitMovementStrategy` (mouse); spawn grace and post-hit immunity do not trigger the switch. `Enemy`
only executes its assigned strategy and never chooses its replacement. These variants keep the same
`EntityKind` and sprite. The hit-and-run strategy tracks its own retreat time after each attack,
independent of the melee cooldown. The strategies use base-class steering helpers, while `Enemy.tick`
still owns separation, knockback, collision and melee attacks:

- `steerToward` is a **hybrid**: charge straight at the target when the body has a clear line
  (sampled with the enemy's own size, so a rat needs a wider gap than a mouse), and otherwise follow
  a `PathField`. This is why open-arena movement looks unchanged while enemies still round corners.
- `separationFrom` pushes out of neighbours' personal space with an inverse falloff, keeping its
  magnitude rather than normalizing, and fans exactly-coincident enemies apart by id along the golden
  angle. Enemies still *pass through* each other — separation only shapes intent, so corridors can
  never gridlock.

`PathField` is a multi-source BFS distance field over the tile grid, rebuilt by `GameWorld` once per
tick from every living player and shared by all enemies. Rebuilding is cheaper than caching it
correctly at this map size and is never stale.

### Waves

`SpawnDirector` runs the wave clock: an intermission, then one enemy at a time until the wave's budget
is spent, then it waits for every enemy to die before the next intermission. `EnemyFactory` picks the
kind from a per-wave weighted table; enemy stats never scale, only count, mix and cadence do.

**Difficulty scales with the number of connected players**, by `PER_PLAYER_SCALE` (1.5) compounding per
extra player: x1, x1.5, x2.25, x3.375. That multiplier ("pressure") applies to three things together —
the wave's enemy budget, the cap on enemies alive at once, and the spawn rate (the cooldown is divided
by it). All three, because scaling the count alone just spreads the same trickle across more guns.

- **It is sampled once, when a wave starts**, from `players.size()` — connected, so a downed player
  still counts and the multiplier does not flicker as people go down and respawn. A player who joins
  mid-wave does not resize the wave running; they count from the next. Wave 1 is prepared in the
  constructor before anyone has joined, which is why the budget is set at the end of the intermission
  rather than in `prepareWave`.
- **The clock freezes while every player is downed**, including the intermission countdown.
- Spawn tiles are chosen at least `MIN_SPAWN_DIST` from every *living* player, so enemies can appear
  near a downed one.

`wave` and `intermission` are published on every `Snapshot`; the multiplier itself is not, so the HUD
cannot show it.

### Pickups

`PickupDirector` drops one health or ammo `Pickup` every few seconds, up to a cap, on a random
plain-floor tile clear of the players and of the crates already out. Each expires on its own timer,
so the steady state is a handful of crates that keep relocating rather than a stockpile.

Two rules are worth knowing before touching them:

- **A pickup is only consumed if it would do something.** A kitten at full health walks over a
  medkit; one with every magazine full walks over a crate. `Pickup.tryCollect` asks
  `ServerPlayer.wantsHealth`/`Arsenal.wantsAmmo` first, and kills the pickup the moment it is taken
  so two players cannot share one. Collection is box overlap, not a radius.
- **Reserve ammo is unlimited**, so what an ammo crate actually buys is the reload it skips —
  `Arsenal.restock` tops up every magazine *and* cancels a reload in flight.

### Collision

- **Actors vs walls:** axis-separated sliding with a bisection for the largest clear fraction, so
  an actor ends flush against the obstacle (`Motion.step`, parameterised by a blocker predicate:
  walls for players, walls-or-living-players for enemies). `Motion.largestClear` is that bisection
  on its own, and `Projectile` reuses it for wall impacts.
- **Projectiles:** *swept*, not point-sampled. Each tick tests the whole segment travelled — a
  ray/slab test against target boxes grown by the round's half-size, plus half-tile sampling for
  walls refined by bisection. This matters: at 30 Hz a rifle round covers ~17 px per tick, wider
  than a mouse, so an endpoint-only test would shoot straight through small enemies and over anything
  touching the shooter. When changing this, keep the step capped at the round's remaining life or
  every weapon silently gains range.

### Conventions

- **Presentation goes through `client/Theme`, not literals.** Colours, fonts, and the panel/text
  primitives live there; it is client-only and deliberately *not* in `GameConfig`, which is for
  numbers both simulations must agree on. New drawing belongs in `Renderer` (world) or `Hud`
  (overlay), and anything that has to move between snapshots belongs in `WorldView` — `Renderer` is
  a pure function of a `Renderer.Scene` and owns no mutable state.
- **Two coordinate spaces per frame, and `Camera` owns the conversion.** `Client.paintComponent`
  applies the camera (which offsets by the viewport and scales by `DisplayPrefs.renderScale`), draws the arena
  in world coordinates, then restores the transform and hands `Hud` the native window size so HUD
  text stays crisp. Do not reimplement the arithmetic: use `Camera.worldX`/`worldY`, which are exact
  inverses of `Camera.apply`.
- **Only the cursor's *screen* position is state.** `InputHandler` stores `mouseX/mouseY` and
  `Client` derives the world position through the camera on demand. A cached world cursor position
  would go stale the moment the camera panned, dragging the aim off the crosshair with it.
- **The map is bigger than the window, so tile drawing is culled.** `Camera.visibleTiles()` produces
  the `Renderer.Tiles` block carried on the `Scene`, and both map passes iterate only that. Anything
  new that walks the whole grid per frame will cost ~4x more than it needs to.
- **All gameplay tuning lives in `GameConfig`**, shared so client and server agree. Put new numbers
  there rather than inline, and keep the reasoning in the javadoc — several constants document
  measured trade-offs, not arbitrary picks. The exception the directors already take: `SpawnDirector`
  and `PickupDirector` keep their cadence, caps and spacing as private constants, because no client
  ever has to agree on them.
- **`Weapon` is tuning and nothing else** — immutable, id-less, and the one exception to
  `GameConfig` for per-weapon numbers. It is abstract with overridable accessors; each concrete
  weapon (e.g. `standard.Pistol`, `scavenger.Nailgun`) exists only to pass its numbers to the base
  constructor, and its constructor is package-private to its family's subpackage.
- **`WeaponFactory` is the Abstract Factory over the arsenal, and each player picks one family.**
  `WeaponFactory.families()` lists them (menu order; the first is also the fallback for an unknown or
  missing name). Each concrete family supplies one product per role — sidearm, scattergun,
  automatic, launcher — plus the `name` sent in `Join.arsenal`, and a title and blurb for the menu.
  The server builds each `ServerPlayer`'s arsenal from that name; the client holds the same family
  for its hotkeys and HUD. Two id spaces: a **slot** is a position within one family (hotkey, HUD
  order, magazine index), a **wire id** is `family base + slot`, unique across families, and is what
  snapshots carry. Appending a role or a family is safe; reordering either renumbers the wire.
  Products are built once and shared by every arsenal, so weapons stay Singletons and code may
  compare them with `==`. `WeaponFactory.decode(wireId)` is the decode path for anything reading a
  snapshot (the renderer, `WorldView`); a family's own `weaponAt(slot)` is for the local player's
  input and HUD.
  Each family is a subpackage holding its weapons and its public factory, so a weapon can only be
  built by the factory beside it; a new one is a new subpackage plus an entry in
  `WeaponFactory.FAMILIES`. Art mirrors the packages: a weapon's sprite key is `<family>/<name>`,
  resolving to `resources/weapons/<family>/<name>.png`.
- **`Arsenal` is what one player carries**, and the only thing a factory hands out: the family, which
  slot is drawn, per-weapon magazines, the reload timer and the fire cooldown. Its ammo state is
  server-authoritative — only `ServerPlayer` ticks an arsenal, and the client reads its own ammo and
  reload off the snapshot rather than simulating them. Its constructor is package-private, so
  `WeaponFactory.createArsenal()` is the only way to get one.
- Entity id ranges keep kinds from colliding: players from 0, enemies from `ENEMY_ID_BASE`,
  projectiles from `PROJECTILE_ID_BASE`, explosions from `EXPLOSION_ID_BASE`.
- `GameConfig.FRIENDLY_FIRE` is a compile-time `false`, so the player-hit branches in `Projectile`
  and `GameWorld.explode` are currently dead code. That is intentional, not rot.
- Player damage immunity (spawn grace and the post-hit i-frame) shares one timer on `ServerPlayer`
  and is surfaced per-entity as `invulnerableFor` so teammates can see who is protected. All damage
  funnels through `ServerPlayer.damage`, so new damage sources get it for free.
- **One liveness flag.** `GameObject.isAlive()` is the only way to ask whether anything is still in
  play; `Actor.damage` clears it at zero health and `ServerPlayer.respawn` sets it back. Knockback
  likewise lives once, on `Actor` (`applyKnockback`/`decayKnockback`), for players and enemies alike.
- **Comments say why, not what.** The essays are gone on purpose; add a comment only for a
  non-obvious trade-off or trap, and keep the pattern labels (see the constraint at the top).

### Audio

`SoundSystem` is the one owner of the sound device, reached through `SoundSystem.get()` rather than
threaded through constructors. `SoundBank` synthesizes every `Sound` in code (there are no audio
files), and `Mixer` mixes all voices in software into a single `SourceDataLine` instead of opening a
`Clip` per shot. World cues are played by `SoundObserver` in response to `GameEvent`s (see Events); the
click that confirms a weapon switch is played directly. `Sound.shotFor` keys on the weapon's slot, so
it follows the `WeaponFactory` role order like the hotkeys and HUD slots do.

`AudioControls` is a Facade over `AudioPrefs` and `SoundSystem`: `louder()`, `quieter()` and
`toggleMute()` change the setting (which saves it) and play the confirmation click — at the new
level, and on unmuting but not on muting, since nothing would be heard. The keyboard shortcuts and the
settings menu both go through it, so any new way to change volume or mute should too. It covers
*changing* audio only; the menu still reads `AudioPrefs.volume()` and `muted()` to display them.

Pass `-Dkittens.audio=off` to a probe that builds a `Client`, or it opens the real device and
plays through the speakers.

### Settings

`Settings` holds everything the player can change: name, last arsenal, key bindings, volume and
mute, render scale and frame rate. It is a Singleton because it is backed by one file
(`~/.kittens/settings.properties`): every save writes all sections from the one in-memory copy, so
a mute toggled in game and an arsenal remembered by the menu cannot overwrite each other. Read
settings through `Settings.get()` rather than adding constants to `Client`, `InputHandler` or `Hud`.
Weapon slots stay on the digit keys and are not bindable.

Players change settings in `SettingsMenu` (S or the button on the arsenal screen, Esc in game), or
by editing the file while the game is closed. Every change saves at once. They take effect at
different times:
- **Immediately:** key bindings, volume and mute.
- **On returning to the game:** the frame rate, and the name and kitten colour, which `Client` sends as
  a `ProfileUpdate` every time the menu closes. Before joining they simply ride on `Join`.
- **Next launch:** the render scale. `Client.RENDER_SCALE` captures it once, because the window is
  never resized.

The menu's own keys (arrows, Enter, Esc) are fixed so a bad binding can always be undone. Opening it
in game calls `InputHandler.release()`, because the match keeps running behind it.

`-Dkittens.settings=<path>` points at a different file and `off` keeps everything in memory. Give
**every probe that builds a `Client`** one of those, or it rewrites the real player's file. Two
clients on one machine also share the file, and so the name, unless each gets its own path.

`GameConfig.cleanName` limits names to safe characters on both ends: the client applies it so the
menu shows what others will see, and the server applies it again because it must not trust what
arrives off the wire.

## Known rough edges

Not bugs to fix on sight — context so you don't mistake them for accidents:

- **One menu, no lobby.** The design brief asks for `Screen` states (`MainMenu` / `Lobby` /
  `InGame`). `ArsenalMenu` and `SettingsMenu` are panels that `Client.main` and
  `Client.showSettings` swap into the frame, not a screen state machine, so a client still joins the
  running match the moment it picks an arsenal. The arsenal is chosen once and cannot change until
  the client restarts. `JoinAccepted.mapId` is carried on the wire but read by nobody — it is the
  seam for the rest.
- **Enemies and players share the `S` tiles.** There is no separate enemy-spawn glyph, so waves can
  only enter where players also spawn, and `SpawnDirector.MIN_SPAWN_DIST` is the only thing keeping
  them out of the room the players are standing in. A level that wants waves to arrive from a chosen
  direction needs a new `Tile` kind.
- **The generator does not guarantee spawn spacing.** `SpawnDirector.MIN_SPAWN_DIST` is a private
  constant `LevelGenerator` cannot see, so on a level whose rooms sit close every wave takes the
  "farthest point" fallback and arrives in view. Measured over 3000 seeds the closest pair of entrances
  ranges from 256 to 560 px against the 520 px threshold.
- Guava is a declared dependency but unused.
