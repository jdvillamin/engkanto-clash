# Engkanto Clash

Engkanto Clash is a Java 17 Swing game for CMSC 137. It is a 2D arena combat
game with Filipino mythical creatures as playable characters.

The game can run in local mode or connect to a Java TCP server for multiplayer.
If no server is available, the client automatically runs the local version.

## Group 5 Info

- Lab section: B5L
- Group members:
  - Alexander John Castro III
  - Sean Caleb Romero
  - Jan Neal Isaac Villamin

## Features

- 2D Java Swing and Java2D gameplay
- four characters: Tikbalang, Kapre, Aswang, and Engkanto
- movement, jumping, platforms, and Aswang glide
- attacks, projectiles, cooldowns, health, damage, healing, and poison
- test dummy for local combat testing
- basic multiplayer through a TCP server
- JSON messages for client input and server game state
- JUnit tests for health behavior

## Tech Used

- Java 17: main programming language for the client and server
- Gradle: build, run, dependency, and test management
- Java Swing: desktop window, panels, and keyboard input handling
- Java2D: sprite, platform, projectile, HUD, lobby, and text rendering
- Java ImageIO: image loading for sprites, logos, tiles, and map backgrounds
- Java TCP sockets: multiplayer client-server communication
- Gson: JSON serialization and deserialization for network messages
- JUnit 5: automated tests for health and combat behavior
- Java Sound API: WAV music, sound effects, and looping audio playback

## How To Run

Build:

```bash
./gradlew build
```

Run the client:

```bash
./gradlew runClient
```

Run the server:

```bash
./gradlew runServer
```

Clients connect to `127.0.0.1:50137` by default. To connect to another machine:

```bash
./gradlew runClient --args="--host=192.168.1.20 --port=50137"
```

Force offline mode:

```bash
./gradlew runClient --args="--offline"
```

## Multi-PC Setup

Install Java 17 JDK on each machine if it is not installed yet:

```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

On the host PC, run:

```bash
./gradlew runServer
```

On each client PC, connect to the host PC's local IP address:

```bash
./gradlew runClient --args="--host=HOST_IP_ADDRESS --port=50137"
```

Run tests:

```bash
./gradlew test
```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

## Docs

- [Gameplay Reference](docs/gameplay.md)
- [LAN Multiplayer Setup](docs/lan-setup.md)
- [System Architecture](docs/system-architecture.md)
