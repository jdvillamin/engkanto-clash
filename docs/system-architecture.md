# System Architecture

This document describes the current codebase state. The project now has the
first Milestone 2 socket integration: a Swing client can connect to a Java TCP
server, send input snapshots, and render server-authoritative player snapshots.
The original local combat prototype still remains available as a fallback when
no server is running.

## Current Runtime Flow

```text
ClientMain
  -> optional NetworkClient TCP connection
  -> GameWindow
      -> GamePanel
          -> fixed update loop at 60 updates/second
          -> Java2D render pass
```

In local mode, the client owns the playable state:

- keyboard input
- player movement and character switching
- platform collision and drop-through behavior
- sprite animation
- local test dummy combat
- health, damage, healing, poison, death, and respawn
- projectile spawning, movement, collision, and one-shot hit behavior
- health and ability cooldown UI

In multiplayer mode:

- `ClientMain` attempts to connect to `127.0.0.1:50137` unless `--offline` is
  provided.
- `KeyboardInput` converts held keys and one-shot ability requests into
  `PlayerInputSnapshot` messages.
- `NetworkClient` writes JSON input messages and keeps the newest
  `GameStateSnapshot` from the server.
- `GamePanel` draws the shared map plus every `PlayerSnapshot` received from the
  server.
- If the connection is unavailable at startup, the client starts in local mode.

## Server Runtime Flow

```text
ServerMain
  -> GameServer
      -> ServerSocket accept loop
      -> fixed update loop at 60 updates/second
      -> broadcast GameStateSnapshot JSON to connected clients
```

The server currently owns:

- player IDs and connection lifecycle
- authoritative player positions, facing, character selection, animation action,
  health, death, and respawn
- movement, jumping, platform landing, and Aswang glide
- basic player-vs-player hit resolution for direct and ranged abilities

## Important Packages

```text
com.engkanto.client
Desktop entry point and Swing window.

com.engkanto.client.net
TCP client connection, JSON message writing, and background server-state
reading.

com.engkanto.client.input
Keyboard state and one-shot action requests.

com.engkanto.client.game
Game loop, arena setup, rendering order, player-vs-dummy hit resolution.

com.engkanto.client.game.character
Character definitions, sprite animation metadata, per-character movement,
cooldown, projectile, and attack rules.

com.engkanto.client.game.combat
Health, damage, poison, floating damage numbers, health UI, and ability UI.

com.engkanto.client.game.entity
Player, projectile, and test dummy entities.

com.engkanto.client.game.world
Platform geometry and tile rendering.

com.engkanto.client.render
Sprite and asset loading helpers plus debug HUD rendering.

com.engkanto.server
Server entry point.

com.engkanto.server.game
Authoritative socket server loop and server-side player simulation.

com.engkanto.common
Shared snapshot models and JSON message DTOs used by both the client and
server.
```

## Combat Flow

```text
KeyboardInput records a move request.
Player consumes the request if the move is not on cooldown.
SpriteAnimator locks the requested attack animation.
CharacterDefinition customizes attack timing, cooldown, movement lock, and hit behavior.
GamePanel checks player/test-dummy overlap for direct attacks.
GamePanel checks projectile/test-dummy hitbox overlap for projectiles.
DamageComponent applies actual clamped damage to HealthComponent.
HealthComponent emits damage, heal, and death events.
```

## Collision Model

- Player world bounds use a `96x96` sprite rectangle.
- The test dummy exposes a `48x72` hitbox.
- Direct attacks use player rectangle vs. test dummy hitbox overlap.
- Projectiles use projectile square vs. test dummy hitbox overlap.
- Floating platforms can be dropped through by holding Down.
- The ground platform remains solid.

## UI Model

The current UI is drawn directly in Java2D:

- `HealthUI` draws player health.
- `AbilityUI` draws `J`, `K`, `E`, and `L` key icons with cooldown state.
- `DebugRenderer` draws development control hints.
- `TestDummy` draws its own label, health bar, hit flash, death label, and
  damage numbers.

## Multiplayer Message Flow

```text
Client -> Server:
  ClientMessage { type: "input", input: PlayerInputSnapshot }

Server -> Client:
  ServerMessage { type: "welcome", playerId: number }
  ServerMessage { type: "state", state: GameStateSnapshot }
```

The network uses newline-delimited JSON over TCP sockets. Gson handles the
message serialization.

## Testing

JUnit coverage currently focuses on `HealthComponent`, including:

- damage and healing clamp behavior
- dead entities ignoring healing
- full-heal reporting
- invalid value handling
- poison tick timing

## Future Architecture Work

The current multiplayer pass focuses on player synchronization and basic PvP
authority. Future work should expand the server model to cover exact
per-character projectile behavior, poison ticking, cooldown UI snapshots, game
lobbies, and in-game chat display.
