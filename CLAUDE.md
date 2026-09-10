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
   and socket code is the thing that preserves those seams.

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
later assertion silently reads a dead target; firing "into open space" on `arena.txt` usually means
firing into a wall a few tiles away; and enemies spawned already touching their target swing in
lockstep, which flatters anything that rate-limits damage.

To *see* the client instead of measuring it, don't try to screen-grab: under WSLg the X11 root
window is black, so `ffmpeg -f x11grab` captures nothing. Render the panel offscreen instead — build
a `Client` on the EDT, `setSize` it to its preferred size, and `paint()` it into a `BufferedImage`.
No window ever has to be shown, and `dispatchEvent` with synthetic `MouseEvent`/`KeyEvent`s drives
firing and movement so HUD states (reload, low health, downed) can be captured. Such a probe needs
`package kittens.client` for `GameClient`, and Gson on the classpath — easiest via
`./gradlew installDist` and `app/build/install/app/lib/*`.

## Architecture

Three packages under `app/src/main/java/kittens/`, with a hard rule: **`common` contains no
rendering and no socket code.** It is the shared simulation both sides run.

- **`common/`** — `math/` (`Vec2`, `Aabb`), `map/` (`TileMap`, `Tile`), `entity/`
  (`GameObject` → `Actor` → `Enemy` → `Rat`/`Mouse`), `weapon/Weapon`, `sim/`
  (`PlayerMotion`, `PathField`), `net/` (the DTOs), `GameConfig`.
- **`server/`** — `Server` (socket accept), `ClientConnection`, `ServerLoop`, `GameWorld`,
  `ServerPlayer`, `Projectile`, `Explosion`, `SpawnDirector`.
- **`client/`** — `GameClient` (networking), `Client` (window, input, prediction, frame loop),
  `WorldView` (between-snapshot smoothing + cosmetic effects), `Renderer` (arena drawing),
  `Hud` (screen-space overlay), `Theme` (palette, fonts, panel/text primitives), `AssetManager`.

### Authority and prediction

`ServerLoop` is a fixed-timestep accumulator on a daemon thread at `GameConfig.TICK_HZ` (30 Hz).
Each tick `GameWorld.tick(dt)` runs a fixed pipeline — players → weapon fire → spawn director →
enemies → projectiles → explosion detonation → cull — then `Server.tick` sends **one snapshot per
client**, because `ackSeq`, `viewerAmmo`, and `viewerReload` are all recipient-specific.

`Client` renders at 180 FPS but sends input at exactly `TICK_HZ`. It predicts the local player by
applying input immediately to its own copy of the movement model, then on each new snapshot
re-anchors to the authoritative position and replays inputs the server hasn't acked, easing small
errors and hard-snapping past a threshold.

Everything the local player does *not* control is smoothed by `WorldView` instead: remote players
and enemies are eased exponentially toward their snapshot positions, and bullets are dead-reckoned
from their angle and weapon speed. The split matters — prediction is reconciled against authority
and lives in `Client`, while `WorldView` is cosmetic and can be retuned without desync risk.

**The critical invariant:** `common/sim/PlayerMotion` is the *single* movement model, run by both
`ServerPlayer.tick` and `Client`'s predictor with the same fixed `1.0 / TICK_HZ` step. It must stay
pure and deterministic. Any divergence — a different timestep, a stray random, an extra force
applied on one side only — surfaces as unexplained rubber-banding, not as an obvious failure.

### Wire protocol

Newline-delimited JSON over TCP (Gson), four message types only, modelled as a sealed interface plus
records: `Join`, `JoinAccepted`, `InputCommand`, `Snapshot`. `MessageCodec` wraps each in a
`{"type":..,"data":..}` envelope so the DTOs stay plain data.

`EntityState` is one flat render-ready row. Two conventions to know:

- `kind` is a sprite tag: `"cat"`, `"rat"`, `"mouse"`, `"bullet"`, `"boom"`.
- **Explosions overload the fields**: for `"boom"`, `angle` carries the current expanding radius and
  `hp` the final radius. Adding a sixth entity kind will likely need a better shape than this.

Adding a field to `EntityState` touches all four construction sites (`ServerPlayer`, `Enemy`,
`Projectile`, `Explosion`). Records serialize by field, and both sides share the class, so the wire
stays consistent automatically.

### Threading

- **Server:** main thread accepts sockets; one reader thread per client (`client-<id>`) decodes
  messages and queues inputs into `ServerPlayer.inbox` (a `ConcurrentLinkedQueue`); the
  `server-loop` thread drains them and owns all mutation. Concurrent collections in `GameWorld`
  exist because `addPlayer`/`removePlayer` arrive from reader threads.
- **Snapshot writes happen on the loop thread** and end in a blocking `flush()`. A merely *slow*
  client (full TCP window, not disconnected) will stall the whole simulation. A per-connection
  outbound queue is the fix if this ever bites.
- **Client:** the `client-net` reader thread writes into a `ConcurrentHashMap` and volatile fields;
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

### Collision

- **Actors vs walls:** axis-separated sliding with a binary search for the largest clear fraction, so
  an actor ends flush against the wall (`PlayerMotion.moveAxis`; `Enemy` has its own near-copy that
  also treats players as blockers).
- **Projectiles:** *swept*, not point-sampled. Each tick tests the whole segment travelled — a
  ray/slab test against target boxes grown by the round's half-size, plus half-tile sampling for
  walls refined by binary search. This matters: at 30 Hz a rifle round covers ~17 px per tick, wider
  than a mouse, so an endpoint-only test would shoot straight through small enemies and over anything
  touching the shooter. When changing this, keep the step capped at the round's remaining life or
  every weapon silently gains range.

### Conventions

- **Presentation goes through `client/Theme`, not literals.** Colours, fonts, and the panel/text
  primitives live there; it is client-only and deliberately *not* in `GameConfig`, which is for
  numbers both simulations must agree on. New drawing belongs in `Renderer` (world) or `Hud`
  (overlay), and anything that has to move between snapshots belongs in `WorldView` — `Renderer` is
  a pure function of a `Renderer.Scene` and owns no mutable state.
- **Two coordinate spaces per frame.** `Client.paintComponent` draws the arena scaled by
  `RENDER_SCALE`, then restores the transform and hands `Hud` the native window size, so HUD text
  stays crisp. Anything screen-space (the crosshair, cursor-driven UI) needs the raw event pixels —
  `Client` keeps both `mouseX/mouseY` (world, for aiming) and `screenMouseX/screenMouseY`.
- **All gameplay tuning lives in `GameConfig`**, shared so client and server agree. Put new numbers
  there rather than inline, and keep the reasoning in the javadoc — several constants document
  measured trade-offs, not arbitrary picks.
- Entity id ranges keep kinds from colliding: players from 0, enemies from `ENEMY_ID_BASE`,
  projectiles from `PROJECTILE_ID_BASE`, explosions from `EXPLOSION_ID_BASE`.
- `GameConfig.FRIENDLY_FIRE` is a compile-time `false`, so the player-hit branches in `Projectile`
  and `GameWorld.explode` are currently dead code. That is intentional, not rot.
- Player damage immunity (spawn grace and the post-hit i-frame) shares one timer on `ServerPlayer`
  and is surfaced per-entity as `invulnerableFor` so teammates can see who is protected. All damage
  funnels through `ServerPlayer.damage`, so new damage sources get it for free.

## Known rough edges

Not bugs to fix on sight — context so you don't mistake them for accidents:

- **`Weapon` is an enum with public final fields.** Elegant now, but a dead end for the Factory /
  Strategy / Decorator work the patterns requirement will need. Converting it is cheap today and
  expensive later.
- **`Client` still owns input.** Rendering is out (`Renderer`, `Hud`, `WorldView`), leaving ~375
  lines of window setup, key/mouse handling, prediction, and the frame loop. The design brief also
  asks for an `InputHandler` and `Screen` states (`MainMenu` / `Lobby` / `InGame`); neither exists,
  so there is no menu and no lobby — a client joins the match the moment it connects.
- **No camera.** The window is a fixed, non-resizable render of the entire map scaled by
  `RENDER_SCALE`, which blocks any level larger than one screen.
- **Wave state never reaches the client.** `SpawnDirector.currentWave()` and `isWaveInProgress()`
  have no callers; nothing displays the wave number or intermission.
- **Enemies spawn on the player `S` tiles**, so waves only ever enter from the four map corners.
- Guava is a declared dependency but unused.
