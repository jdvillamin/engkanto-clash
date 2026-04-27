# Engkanto Clash

Engkanto Clash is a networked 2D Java game for CMSC 137. It features a desktop
client, an authoritative Java socket server, multiplayer gameplay for at least
four players, sprite-based rendering, and in-game chat.

## Technology Stack

### Java 21

The project uses Java 21 as the main programming language and runtime. Java 21
is a modern long-term support version suitable for desktop applications,
networking, and game loop development.

### Gradle

Gradle manages compilation, dependencies, testing, and application run tasks for
the client and server.

### Java Swing and Java2D

The client uses Swing for the desktop window and Java2D for rendering the game
world, sprites, interface elements, and animations.

### Custom `BufferedImage` Sprites

Sprites and spritesheets are loaded and drawn through lightweight custom helper
classes built around Java's `BufferedImage` API.

### Java TCP Sockets

The server and clients communicate through Java `ServerSocket` and `Socket`
connections. The server owns the official game state and broadcasts updates to
connected clients.

### Gson

Network messages are encoded as JSON using Gson, making client-server messages
readable, debuggable, and easy to extend.

### JUnit 5

JUnit 5 is used for testing shared game models, server-side rules, collision
logic, and message handling.

## Running the Project

Build the project:

```bash
./gradlew build
```

Run the server:

```bash
./gradlew runServer
```

Run a client:

```bash
./gradlew runClient
```

Run tests:

```bash
./gradlew test
```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.
