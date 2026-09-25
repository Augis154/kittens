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
2. **23 OOP design patterns must eventually be implemented**, added gradually over months. The
   prototype is deliberately pattern-free, written to leave *seams* where patterns will later go.
   **Do not refactor code into design patterns unless asked.** Keeping the core free of rendering
   and socket code is the thing that preserves those seams. Where a pattern already fell out of
   the structure it is named in that class's comment (Template Method / Strategy seam in `Enemy`,
   Singleton weapons shared by every `Arsenal`, Command in `InputCommand`, Facade in `GameWorld`,
   Game Loop in `ServerLoop`, Flyweight cache in `AssetManager`, Factory in `SpawnDirector`,
   Abstract Factory in `WeaponFactory`); leave those labels accurate when touching the class.

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
rate-limits damage.

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

- **`common/`** — `math/` (`Vec2`, `Aabb`), `map/` (`TileMap`, `Tile`), `entity/`
  (`GameObject` → `Actor` → `Enemy` → `Rat`/`Mouse`, `EnemyFactory`), `weapon/`
  (`Weapon` → `Pistol`/`Shotgun`/`Rifle`/`Bazooka`, plus `WeaponFactory` and `Arsenal`), `sim/` (`Motion`, `PathField`),
  `net/` (the DTOs, `EntityKind`, `MessageCodec`, `MessageChannel`), `GameConfig`.
- **`server/`** — `Server` (accept loop + per-client reader threads), `ServerLoop`, `GameWorld`,
  `ServerPlayer` (carries a `common.weapon.Arsenal`), `Projectile`, `Explosion`, `Pickup`,
  `SpawnDirector`, `PickupDirector`.
- **`client/`** — `GameClient` (networking; publishes each snapshot as an immutable `Frame`),
  `Client` (window, timers, scene assembly), `InputHandler` (keys, mouse, cursor), `Predictor`
  (local-player prediction and reconciliation), `Camera` (viewport, follow, world/screen
  conversion), `WorldView` (between-snapshot smoothing + cosmetic effects), `Renderer` (arena
  drawing), `Hud` (screen-space overlay), `Theme` (palette, fonts, panel/text primitives),
  `AssetManager`.

### Levels

Plain text loaded by `TileMap`, one character per tile: `.` floor, `#` wall, `S` spawn.
`GameConfig.MAP_RESOURCE` picks which one. `maps/sewers.txt` is the default — 60x36 tiles (four
rooms, a central hub, a corridor ring), deliberately larger than the 25x15-tile viewport so the
camera has somewhere to scroll. `maps/arena.txt` is the original single-screen map, exactly one
viewport, which is why it stays around for probes.

Plain `.` floor is also where pickups land: `TileMap.floorPoints()` is every open cell that is
*not* a spawn point, precomputed at load, and `PickupDirector` samples it. So marking a cell `S`
takes it out of the pickup pool as well as adding a wave entrance.

A level has to be rectangular, sealed by walls on every edge, and have all its open cells mutually
reachable — **nothing checks any of this at load time**, so a level with a walled-off room simply
strands enemies and pickups in it. Corridors want to be at least 2 tiles wide (a rat's box is 22px
against a 32px tile), and avoiding lanes that run the full width or height keeps sightlines from
dominating.

### Authority and prediction

`ServerLoop` is a fixed-timestep accumulator on a daemon thread at `GameConfig.TICK_HZ` (30 Hz).
Each tick `GameWorld.tick(dt)` runs a fixed pipeline — players → weapon fire → pickups → spawn
director → enemies → projectiles → explosion detonation → cull — then `Server.tick` sends **one
snapshot per client**, because `ackSeq`, `viewerAmmo`, and `viewerReload` are all recipient-specific.

`Client` renders at 180 FPS but sends input at exactly `TICK_HZ`. `Predictor` applies input to
the local player immediately, then on each new snapshot (detected by the `Frame`'s tick changing)
re-anchors to the authoritative position and replays inputs the server hasn't acked, easing small
errors and hard-snapping past a threshold.

Everything the local player does *not* control is smoothed by `WorldView` instead: remote players
and enemies are eased exponentially toward their snapshot positions, and bullets are dead-reckoned
from their angle and weapon speed. The split matters — prediction is reconciled against authority
and lives in `Predictor`, while `WorldView` is cosmetic and can be retuned without desync risk.

**The critical invariant:** `common/sim/Motion` is the *single* movement model. Its player overload
is run by both `ServerPlayer.tick` and `Predictor` with the same fixed `GameConfig.TICK_DT` step,
and must stay pure and deterministic. Any divergence — a different timestep, a stray random, an
extra force applied on one side only — surfaces as unexplained rubber-banding, not as an obvious
failure. Enemies use the same `Motion.step` with a blocker predicate that also refuses living
players; that path is server-only, so it is free to change.

### Wire protocol

Newline-delimited JSON over TCP (Gson), four message types only, modelled as a sealed interface plus
records: `Join`, `JoinAccepted`, `InputCommand`, `Snapshot`. `MessageCodec` wraps each in a
`{"type":..,"data":..}` envelope so the DTOs stay plain data.

`EntityState` is one flat render-ready row. Two conventions to know:

- `kind` is the `EntityKind` enum (`CAT`, `RAT`, `MOUSE`, `BULLET`, `BOOM`, `HEALTH`, `AMMO`),
  serialized by name. Its `isEnemy()`/`isPickup()`/`isActor()` are the only way code should ask
  "what sort of thing is this"; `sprite()` is the lower-case resource name.
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
  from reader threads; enemies, projectiles, explosions and pickups are plain lists touched by the
  loop thread only. Keep it that way — adding a concurrent collection there implies a threading
  rule that does not exist.
- **Snapshot writes happen on the loop thread** and end in a blocking `flush()`. A merely *slow*
  client (full TCP window, not disconnected) will stall the whole simulation. A per-connection
  outbound queue is the fix if this ever bites.
- **Client:** the `client-net` reader thread publishes each snapshot as one immutable
  `GameClient.Frame` through a single volatile, so a render frame never sees half a snapshot;
  everything else (input state, prediction, rendering) is Swing EDT only, driven by two `Timer`s.

### Enemy AI

`Enemy.computeMoveDirection` is the designated AI seam. `Rat` chases; `Mouse` chases with sinusoidal
weaving. Both go through two base-class helpers:

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
  applies the camera (which offsets by the viewport and scales by `RENDER_SCALE`), draws the arena
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
  weapon (`Pistol`, `Shotgun`, `Rifle`, `Bazooka`) exists only to pass its numbers to the base
  constructor, and their constructors are package-private.
- **`WeaponFactory` is the Abstract Factory over the arsenal, and owns the wire ids.** The family
  in play is the one line `WeaponFactory.ACTIVE`; each concrete family supplies one product per
  role — sidearm, scattergun, automatic, launcher — and **that role order defines the wire ids**,
  which are also the hotkeys, the HUD slot order and the magazine indices. Appending a role is safe;
  reordering renumbers all of those at once. Products are built once and shared by every arsenal, so
  weapons stay Singletons and code may compare them with `==`. `WeaponFactory.weapon(id)`/`count()`
  are the decode path for anything holding a wire id (the renderer, the HUD, the client's hotkeys);
  `StandardWeaponFactory` is package-private, so a new family is a new class in `common/weapon`
  plus that one line.
- **`Arsenal` is what one player carries**, and the only thing a factory hands out: the family, which
  role is drawn, per-weapon magazines, the reload timer and the fire cooldown. Its ammo state is
  server-authoritative — only `ServerPlayer` ticks an arsenal, and the client reads its own ammo and
  reload off the snapshot rather than simulating them. Its constructor is package-private, so
  `WeaponFactory.newArsenal()` is the only way to get one.
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

## Known rough edges

Not bugs to fix on sight — context so you don't mistake them for accidents:

- **No menu, no lobby.** The design brief asks for `Screen` states (`MainMenu` / `Lobby` /
  `InGame`); none exist, so a client joins the match the moment it connects. `Join.clientName` and
  `JoinAccepted.mapId` are carried on the wire but read by nobody — they are the seam for that.
- **Wave state never reaches the client.** `SpawnDirector`'s wave number and intermission are
  private fields with no accessor; nothing displays them.
- **Enemies and players share the `S` tiles.** There is no separate enemy-spawn glyph, so waves can
  only enter where players also spawn, and `SpawnDirector.MIN_SPAWN_DIST` is the only thing keeping
  them out of the room the players are standing in. A level that wants waves to arrive from a chosen
  direction needs a new `Tile` kind.
- Guava is a declared dependency but unused.
