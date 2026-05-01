# Engkanto Clash

Engkanto Clash is a Java 17 / Swing 2D game project for CMSC 137. The current
implementation is a local playable combat prototype with character switching,
sprite animations, platform movement, health and damage systems, projectiles,
cooldowns, and a test dummy target.

The repository still includes package roots for future client/server/common
architecture. At this stage, the server entry point is only a placeholder and
the active gameplay runs locally in the Swing client.

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

Run a client:

```bash
./gradlew runClient
```

Run tests:

```bash
./gradlew test
```

The placeholder server entry point can be launched with:

```bash
./gradlew runServer
```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

## Documentation

- [Gameplay Reference](docs/gameplay.md)
- [System Architecture](docs/system-architecture.md)
