# Engkanto Clash

Engkanto Clash is a Java 17 / Swing 2D game project for CMSC 137. The current
implementation is a local playable combat prototype with character switching,
sprite animations, platform movement, health and damage systems, projectiles,
cooldowns, and a test dummy target.

Milestone 2 socket integration is now underway. The Swing client can connect to
a Java TCP server, send keyboard input snapshots, and render the authoritative
player snapshots broadcast by the server. If no server is available, the client
falls back to the local single-player prototype.

## Current Features

- Java Swing desktop client and Java2D rendering
- 60 updates-per-second game loop
- four playable character sprites: Tikbalang, Kapre, Aswang, and Engkanto
- movement, jumping, Aswang glide, and drop-through floating platforms
- wide `1280x704` arena with tiled platforms
- health, damage, healing, death, revive, and poison status behavior
- direct attack and projectile hit resolution against a test dummy
- one-hit-per-attack and one-hit-per-projectile damage guards
- player health UI
- `J`, `K`, `E`, and `L` cooldown key UI
- Java TCP socket server for up to four multiplayer clients
- JSON client input and server game-state messages
- authoritative server movement, respawn, and basic player-vs-player hit
  resolution
- JUnit tests for core health behavior

## Technology Stack

- Java 17
- Gradle
- Java Swing and Java2D
- custom `BufferedImage` sprite and tile rendering helpers
- JUnit 5
- Gson dependency reserved for future JSON/network work

## Running The Project

Build the project:

```bash
./gradlew build
```

Run a local client:

```bash
./gradlew runClient
```

Run a multiplayer server:

```bash
./gradlew runServer
```

Then launch one or more clients. By default, clients try
`127.0.0.1:50137` and fall back to local mode if the server is unavailable:

```bash
./gradlew runClient
```

Use explicit client options when connecting to another machine:

```bash
./gradlew runClient --args="--host=192.168.1.20 --port=50137"
```

Force local mode:

```bash
./gradlew runClient --args="--offline"
```

Run tests:

```bash
./gradlew test
```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

## Documentation

- [Gameplay Reference](docs/gameplay.md)
- [System Architecture](docs/system-architecture.md)
