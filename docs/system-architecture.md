# System Architecture

Engkanto Clash has a Swing client and an optional Java TCP server. If the client
cannot connect to a server, it starts the local test mode.

## Client Flow

```text
ClientMain
  -> tries NetworkClient unless --offline is used
  -> GameWindow
      -> LobbyPanel if connected
      -> GamePanel for the actual game
```

The client handles:

- Swing window and Java2D rendering
- keyboard input
- local test mode
- lobby screen
- chat display and input
- health, cooldown, timer, leaderboard, and results UI
- drawing server snapshots in multiplayer

`GamePanel` runs at `60` updates per second.

## Local Mode

Local mode is for testing gameplay without a server. It owns the player, test
dummy, movement, platforms, attacks, projectiles, health, poison, respawn, and
cooldown UI.

## Multiplayer Mode

```text
ServerMain
  -> GameServer
      -> accepts TCP clients
      -> lobby phase
      -> game phase
      -> game-over phase
```

The server handles:

- up to `4` players
- character selection and ready state
- `10` second countdown when all players are ready
- `180` second match timer
- authoritative movement, jumping, platforms, and Aswang glide
- skill cooldowns
- player HP, deaths, respawns, and respawn invulnerability
- PvP melee hits, projectiles, poison, Engkanto roots, and kills

The client sends input snapshots. The server sends back snapshots that the
client renders.

## Network Messages

Client to server:

- `select_character`
- `ready`
- `input`
- `chat`

Server to client:

- `welcome`
- `lobby_state`
- `game_start`
- `state`
- `chat`
- `disconnect`

Messages are newline-delimited JSON. Gson is used for serialization.

## Main Packages

- `com.engkanto.client`: client entry point and window
- `com.engkanto.client.lobby`: multiplayer lobby UI
- `com.engkanto.client.net`: TCP client
- `com.engkanto.client.input`: keyboard input snapshots
- `com.engkanto.client.game`: game loop, rendering, local combat checks
- `com.engkanto.client.game.character`: character skills and animations
- `com.engkanto.client.game.combat`: health, damage, poison, and UI
- `com.engkanto.client.game.entity`: players, projectiles, dummy, remote renderers
- `com.engkanto.client.game.world`: platforms
- `com.engkanto.client.render`: sprite loading and debug rendering
- `com.engkanto.server`: server entry point
- `com.engkanto.server.game`: server game loop and PvP simulation
- `com.engkanto.common`: shared models and network DTOs

## Current Notes

- Local mode and server mode both have combat logic, so skill values must be
  updated in both places.
- Local mode is mainly for testing against the dummy.
- Multiplayer is the main mode for PvP, lobby, chat, timer, kills, and results.
- The generated app launcher starts the client. The server is run with
  `runServer`.

## Tests

JUnit tests currently focus on `HealthComponent`, including damage, healing,
death, revive, invalid values, and poison timing.
