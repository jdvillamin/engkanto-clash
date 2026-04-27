# System Architecture

Engkanto Clash uses a client-server architecture with an authoritative Java
server. Each player runs a Java desktop client, and all clients connect to the
same server through TCP sockets.

```text
+----------------+        TCP Socket        +----------------+
| Java Client 1  | <----------------------> |                |
+----------------+                          |                |
                                            |                |
+----------------+        TCP Socket        |                |
| Java Client 2  | <----------------------> |  Java Server   |
+----------------+                          |                |
                                            |                |
+----------------+        TCP Socket        |                |
| Java Client 3  | <----------------------> |                |
+----------------+                          |                |
                                            |                |
+----------------+        TCP Socket        +----------------+
| Java Client 4  | <----------------------> |
+----------------+
```

## Core Rule

```text
Client sends input.
Server decides what happens.
Client renders the result.
```

The client is responsible for presentation and input. The server is responsible
for game truth.

## Client Responsibilities

- open the game window
- run the render loop
- read keyboard input
- load and draw sprites
- send player actions to the server
- render the latest game state from the server
- display chat messages

## Server Responsibilities

- accept client connections
- assign player IDs
- track connected players
- maintain the official game state
- process movement and attacks
- resolve collisions, damage, deaths, and scores
- broadcast state updates to all clients
- relay chat messages

## Message Flow

Client-to-server messages:

- `JOIN_GAME`
- `MOVE_UP`
- `MOVE_DOWN`
- `MOVE_LEFT`
- `MOVE_RIGHT`
- `ATTACK`
- `CHAT_MESSAGE`

Server-to-client messages:

- `WELCOME`
- `PLAYER_JOINED`
- `GAME_STATE_UPDATE`
- `PLAYER_HIT`
- `PLAYER_LEFT`
- `CHAT_BROADCAST`

## Gameplay Flow

```text
Player presses W.
Client sends MOVE_UP to the server.
Server updates that player's position.
Server broadcasts GAME_STATE_UPDATE.
All clients redraw the updated player positions.
```

## Chat Flow

```text
Client sends CHAT_MESSAGE.
Server receives the message.
Server broadcasts CHAT_BROADCAST.
All clients display the message.
```

## Package Mapping

```text
com.engkanto.client
Desktop client entry point, rendering, input, and local client state.

com.engkanto.server
Server entry point, connection handling, and authoritative game loop.

com.engkanto.common
Shared models, network messages, constants, and simple utility classes.
```
