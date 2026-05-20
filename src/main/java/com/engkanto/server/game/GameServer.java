/*
 * Key Objects / Libraries Used
 *
 * Gson
 * - Converts Java objects to JSON and JSON strings back to Java objects.
 * - Used for serializing server messages and deserializing client messages.
 * - Reference: https://github.com/google/gson/blob/main/UserGuide.md
 *
 * ServerSocket
 * - Listens for incoming TCP connections on a specific port.
 * - Used to accept new client connections in the accept thread.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/ServerSocket.html
 *
 * Socket
 * - Represents an individual client connection to the server.
 * - Used to read from and write to each connected client.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/Socket.html
 *
 * PrintWriter
 * - Writes text data to an output stream.
 * - Used to send JSON messages from the server to each client.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintWriter.html
 *
 * BufferedReader
 * - Reads text data from an input stream efficiently, line by line.
 * - Used to receive JSON messages from each client.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html
 *
 * Thread
 * - Allows code to run in the background.
 * - Used for the client accept loop and per-client reader threads.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
 *
 * synchronized / Object lock
 * - Ensures only one thread accesses shared data at a time.
 * - Used to protect the clients and players maps from concurrent modification.
 * - Reference: https://docs.oracle.com/javase/tutorial/essential/concurrency/syncmeth.html
 *
 * LinkedHashMap
 * - A map that maintains insertion order.
 * - Used to store clients and players so iteration order is consistent.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html
 *
 * Iterator
 * - Allows safe removal of elements while iterating a collection.
 * - Used when removing projectiles that hit a player or go off screen.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html
 */

package com.engkanto.server.game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.engkanto.common.model.GameStateSnapshot;
import com.engkanto.common.model.LobbyPlayerSnapshot;
import com.engkanto.common.model.LobbySnapshot;
import com.engkanto.common.model.PlayerSnapshot;
import com.engkanto.common.net.ClientMessage;
import com.engkanto.common.net.NetworkConfig;
import com.engkanto.common.net.ServerMessage;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public final class GameServer {
    private static final double TARGET_UPDATES_PER_SECOND = 60.0;
    private static final double COUNTDOWN_SECONDS = 10.0;
    private static final double GAME_DURATION_SECONDS = 180.0;
    private static final double RESULTS_SECONDS = 8.0;
    private static final int SCREEN_HEIGHT = 704;

    private static final double[][] SPAWN_CANDIDATES = {
            {160, 512}, {640, 512}, {1120, 512},
            {256, 396}, {568, 296}, {880, 396},
            {408, 196}, {720, 196}, {1112, 296}
    };

    private enum Phase { LOBBY, IN_GAME, GAME_OVER }

    private final Gson gson = new Gson();
    private final Object lock = new Object();
    private final Map<Integer, ClientConnection> clients = new LinkedHashMap<>();
    private final Map<Integer, ServerPlayer> players = new LinkedHashMap<>();
    private final List<ServerProjectile> projectiles = new ArrayList<>();
    private final List<ServerPlatform> platforms = createPlatforms();
    private final int port;

    private volatile boolean running;
    private ServerSocket serverSocket;
    private long tick;
    private int nextPlayerId = 1;
    private Phase phase = Phase.LOBBY;
    private boolean countdownActive;
    private double countdownRemaining;
    private double gameSecondsRemaining = GAME_DURATION_SECONDS;
    private double resultsSecondsRemaining;

    public GameServer(int port) {
        this.port = port;
    }

    /*
     * runBlocking()
     *
     * - Entry point that starts the server.
     * - Opens a ServerSocket on the configured port to listen for connections.
     * - Starts a daemon thread to accept incoming client connections.
     * - Runs the main game loop on the current thread (blocks until the server stops).
     */
    public void runBlocking() throws IOException {
        running = true;
        serverSocket = new ServerSocket(port);
        Thread acceptThread = new Thread(this::acceptClients, "engkanto-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        System.out.println("Engkanto Clash server listening on port " + port);
        runGameLoop();
    }

    /*
     * acceptClients()
     *
     * - Runs in a background thread to accept incoming client connections.
     * - Loops until the server is no longer running.
     * - Passes each accepted socket to handleAcceptedClient for setup.
     */
    private void acceptClients() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                handleAcceptedClient(socket);
            } catch (IOException exception) {
                if (running) {
                    System.err.println("Accept failed: " + exception.getMessage());
                }
            }
        }
    }

    /*
    * handleAcceptedClient(Socket socket)
    *
    * - Handles a newly connected client socket.
    * - Rejects the client if the game is not in the lobby phase.
    * - Rejects the client if the server already reached the maximum number of players.
    * - Creates a new player ID, ServerPlayer, and ClientConnection for accepted clients.
    * - Sends a welcome message to tell the client its assigned player ID.
    * - Starts a reader thread so the server can receive messages from that client.
    * - Updates and broadcasts the lobby state after the client joins.
    * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/Socket.html
    */
    private void handleAcceptedClient(Socket socket) throws IOException {
        synchronized (lock) {
            if (phase != Phase.LOBBY) {
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                writer.println(gson.toJson(ServerMessage.disconnect("Game already in progress")));
                socket.close();
                return;
            }
            if (clients.size() >= NetworkConfig.MAX_PLAYERS) {
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                writer.println(gson.toJson(ServerMessage.disconnect("Server is full")));
                socket.close();
                return;
            }

            int playerId = nextPlayerId++;
            int defaultCharacter = (playerId - 1) % ServerPlayer.CHARACTER_NAMES.length;
            ServerPlayer player = createPlayer(playerId);
            ClientConnection client = new ClientConnection(playerId, socket);
            client.characterIndex = defaultCharacter;
            clients.put(playerId, client);
            players.put(playerId, player);
            client.send(ServerMessage.welcome(playerId));
            client.startReader();
            System.out.println("Player " + playerId + " connected");
        }
        checkCountdownState();
        broadcastLobbyState();
    }

    private ServerPlayer createPlayer(int playerId) {
        double groundY = SCREEN_HEIGHT - 96 - ServerPlayer.SIZE;
        double x = 160.0 + ((playerId - 1) * 220.0);
        return new ServerPlayer(playerId, x, groundY);
    }

    /*
     * runGameLoop()
     *
     * - The main server game loop that runs on the main thread.
     * - Uses a fixed-timestep accumulator to update at 60 ticks per second.
     * - Calls update() for each accumulated tick.
     * - Sleeps briefly between iterations to avoid burning CPU.
     */
    private void runGameLoop() {
        final double secondsPerUpdate = 1.0 / TARGET_UPDATES_PER_SECOND;
        final double nanosPerUpdate = secondsPerUpdate * 1_000_000_000.0;
        long previousTime = System.nanoTime();
        double accumulatedUpdates = 0.0;

        while (running) {
            long currentTime = System.nanoTime();
            accumulatedUpdates += (currentTime - previousTime) / nanosPerUpdate;
            previousTime = currentTime;

            while (accumulatedUpdates >= 1.0) {
                update(secondsPerUpdate);
                accumulatedUpdates--;
            }

            sleepBriefly();
        }
    }

    /*
     * update(double deltaSeconds)
     *
     * - Called once per server tick to advance the game state.
     * - In the lobby phase, delegates to updateLobby for countdown logic.
     * - In the game-over phase, broadcasts results briefly before returning to lobby.
     * - During gameplay, updates all players, resolves combat/vines/poisons/projectiles,
     *   handles respawns, checks the game timer, and broadcasts the new state to all clients.
     */
    private void update(double deltaSeconds) {
        if (phase == Phase.LOBBY) {
            updateLobby(deltaSeconds);
            return;
        }
        if (phase == Phase.GAME_OVER) {
            updateGameOver(deltaSeconds);
            return;
        }

        List<ClientConnection> connections;
        GameStateSnapshot snapshot;
        synchronized (lock) {
            gameSecondsRemaining = Math.max(0.0, gameSecondsRemaining - deltaSeconds);
            for (ServerPlayer player : players.values()) {
                player.update(deltaSeconds, platforms);
            }
            resolvePlayerCombat();
            resolveVineRoots();
            resolvePoisons(deltaSeconds);
            updateProjectiles(deltaSeconds);
            respawnReadyPlayers();
            if (gameSecondsRemaining <= 0.0) {
                phase = Phase.GAME_OVER;
                resultsSecondsRemaining = RESULTS_SECONDS;
                System.out.println("Game over!");
            }
            tick++;
            snapshot = createSnapshot();
            connections = new ArrayList<>(clients.values());
        }
        ServerMessage message = ServerMessage.gameState(snapshot);
        for (ClientConnection connection : connections) {
            connection.send(message);
        }
    }

    /*
     * updateGameOver(double deltaSeconds)
     *
     * - Keeps broadcasting the final results for a short results phase.
     * - After the results timer ends, resets players/readiness and sends everyone back to lobby.
     */
    private void updateGameOver(double deltaSeconds) {
        List<ClientConnection> connections = null;
        ServerMessage message = null;
        boolean broadcastResults;

        synchronized (lock) {
            resultsSecondsRemaining = Math.max(0.0, resultsSecondsRemaining - deltaSeconds);

            if (resultsSecondsRemaining <= 0.0) {
                resetToLobby();
                connections = new ArrayList<>(clients.values());
                message = ServerMessage.lobbyState(createLobbySnapshot());
                broadcastResults = false;
            } else {
                broadcastResults = true;
            }
        }

        if (broadcastResults) {
            broadcastGameState();
            return;
        }

        for (ClientConnection connection : connections) {
            connection.send(message);
        }
    }

    /*
     * updateLobby(double deltaSeconds)
     *
     * - Handles the lobby countdown timer.
     * - Ticks down the countdown and broadcasts lobby state when the displayed second changes.
     * - When the countdown reaches zero, locks in character selections and transitions to the game phase.
     */
    private void updateLobby(double deltaSeconds) {
        List<ClientConnection> connections = null;
        LobbySnapshot snapshot = null;
        boolean gameStarting = false;

        synchronized (lock) {
            if (!countdownActive) {
                return;
            }
            int previousSecond = (int) Math.ceil(countdownRemaining);
            countdownRemaining -= deltaSeconds;
            int currentSecond = (int) Math.ceil(countdownRemaining);

            if (countdownRemaining <= 0) {
                gameStarting = true;
                for (Map.Entry<Integer, ClientConnection> entry : clients.entrySet()) {
                    ServerPlayer player = players.get(entry.getKey());
                    if (player != null) {
                        player.setCharacterIndex(entry.getValue().characterIndex);
                    }
                }
                phase = Phase.IN_GAME;
                gameSecondsRemaining = GAME_DURATION_SECONDS;
                countdownActive = false;
                connections = new ArrayList<>(clients.values());
            } else if (currentSecond != previousSecond) {
                snapshot = createLobbySnapshot();
                connections = new ArrayList<>(clients.values());
            }
        }

        if (gameStarting) {
            System.out.println("Game started!");
            ServerMessage message = ServerMessage.gameStart();
            for (ClientConnection connection : connections) {
                connection.send(message);
            }
        } else if (snapshot != null) {
            ServerMessage message = ServerMessage.lobbyState(snapshot);
            for (ClientConnection connection : connections) {
                connection.send(message);
            }
        }
    }

    /*
     * broadcastGameState()
     *
     * - Builds a GameStateSnapshot and sends it to all connected clients.
     * - Used during the game-over phase to keep sending the final state.
     */
    private void broadcastGameState() {
        List<ClientConnection> connections;
        GameStateSnapshot snapshot;
        synchronized (lock) {
            snapshot = createSnapshot();
            connections = new ArrayList<>(clients.values());
        }
        ServerMessage message = ServerMessage.gameState(snapshot);
        for (ClientConnection connection : connections) {
            connection.send(message);
        }
    }

    /*
     * resetToLobby()
     *
     * - Clears match state and marks all connected clients unready.
     * - Recreates server players so health, kills, cooldowns, projectiles, and effects reset.
     * - Keeps each client's current character selection so they can ready up or reselect.
     */
    private void resetToLobby() {
        phase = Phase.LOBBY;
        countdownActive = false;
        countdownRemaining = -1;
        gameSecondsRemaining = GAME_DURATION_SECONDS;
        resultsSecondsRemaining = 0.0;
        tick = 0;
        projectiles.clear();
        players.clear();

        for (ClientConnection connection : clients.values()) {
            connection.ready = false;
            ServerPlayer player = createPlayer(connection.playerId);
            player.setCharacterIndex(connection.characterIndex);
            players.put(connection.playerId, player);
        }

        System.out.println("Returned to lobby.");
    }

    /*
     * checkCountdownState()
     *
     * - Checks if all connected clients are ready to start the game.
     * - Starts the countdown timer if everyone is ready.
     * - Cancels the countdown if any player is not ready or no players are connected.
     */
    private void checkCountdownState() {
        synchronized (lock) {
            if (phase != Phase.LOBBY) {
                return;
            }
            if (clients.isEmpty()) {
                countdownActive = false;
                return;
            }
            boolean allReady = true;
            for (ClientConnection connection : clients.values()) {
                if (!connection.ready) {
                    allReady = false;
                    break;
                }
            }
            if (allReady) {
                if (!countdownActive) {
                    countdownActive = true;
                    countdownRemaining = COUNTDOWN_SECONDS;
                    System.out.println("All players ready! Countdown started.");
                }
            } else {
                if (countdownActive) {
                    System.out.println("Countdown cancelled — not all players ready.");
                }
                countdownActive = false;
                countdownRemaining = -1;
            }
        }
    }

    /*
     * broadcastLobbyState()
     *
     * - Builds a LobbySnapshot and sends it to all connected clients.
     * - Only broadcasts if the game is still in the lobby phase.
     */
    private void broadcastLobbyState() {
        LobbySnapshot snapshot;
        List<ClientConnection> connections;
        synchronized (lock) {
            if (phase != Phase.LOBBY) {
                return;
            }
            snapshot = createLobbySnapshot();
            connections = new ArrayList<>(clients.values());
        }
        ServerMessage message = ServerMessage.lobbyState(snapshot);
        for (ClientConnection connection : connections) {
            connection.send(message);
        }
    }

    /*
     * createLobbySnapshot()
     *
     * - Builds a LobbySnapshot with each player's ID, character selection, and ready status.
     * - Includes the countdown timer value if the countdown is active.
     */
    private LobbySnapshot createLobbySnapshot() {
        LobbySnapshot snapshot = new LobbySnapshot();
        for (ClientConnection connection : clients.values()) {
            LobbyPlayerSnapshot lobbyPlayer = new LobbyPlayerSnapshot();
            lobbyPlayer.id = connection.playerId;
            lobbyPlayer.characterIndex = connection.characterIndex;
            lobbyPlayer.characterName = ServerPlayer.CHARACTER_NAMES[connection.characterIndex];
            lobbyPlayer.ready = connection.ready;
            snapshot.players.add(lobbyPlayer);
        }
        snapshot.countdownSeconds = countdownActive ? (int) Math.ceil(countdownRemaining) : -1;
        return snapshot;
    }

    /*
     * resolvePlayerCombat()
     *
     * - Checks each player for a pending attack and resolves it.
     * - For ranged attacks, creates a ServerProjectile and adds it to the projectile list.
     * - For melee attacks, checks all other players for overlap and applies damage.
     * - Awards a kill to the attacker if the target dies.
     * - Applies poison to the target if the attack is a poison-type move.
     */
    private void resolvePlayerCombat() {
        for (ServerPlayer attacker : players.values()) {
            if (!attacker.hasAttackReady()) {
                continue;
            }
            if (attacker.isPendingRangedAttack()) {
                projectiles.add(attacker.createProjectile());
                attacker.markAttackResolved();
            } else {
                for (ServerPlayer target : players.values()) {
                    if (attacker.canHit(target)) {
                        boolean killed = target.takeDamage(attacker.getPendingDamage());
                        if (killed) {
                            attacker.addKill();
                        }
                        if (attacker.isPendingPoison() && !target.isDead()) {
                            target.applyPoison(attacker.getId());
                        }
                    }
                }
                attacker.markAttackResolved();
            }
        }
    }

    /*
     * resolvePoisons(double deltaSeconds)
     *
     * - Ticks down active poison effects on all living players.
     * - If a poison tick kills a player, awards a kill to the poison's owner.
     */
    private void resolvePoisons(double deltaSeconds) {
        for (ServerPlayer player : players.values()) {
            if (player.isDead()) {
                continue;
            }
            int killerOwnerId = player.tickPoisons(deltaSeconds);
            if (killerOwnerId >= 0) {
                ServerPlayer killer = players.get(killerOwnerId);
                if (killer != null) {
                    killer.addKill();
                }
            }
        }
    }

    /*
     * resolveVineRoots()
     *
     * - Checks each player with a pending vine root ability.
     * - If the vine overlaps another living, non-invulnerable player, applies a root to them.
     * - Marks the vine root as resolved after checking all targets.
     */
    private void resolveVineRoots() {
        for (ServerPlayer caster : players.values()) {
            if (!caster.hasVineRootReady()) {
                continue;
            }
            for (ServerPlayer target : players.values()) {
                if (target == caster || target.isDead() || target.isInvulnerable()) {
                    continue;
                }
                if (caster.overlapsVineRoot(target)) {
                    target.applyRoot(caster.getVineRootDuration());
                }
            }
            caster.markVineRootResolved();
        }
    }

    /*
     * updateProjectiles(double deltaSeconds)
     *
     * - Moves all active projectiles and checks for collisions.
     * - Removes projectiles that go off screen.
     * - If a projectile overlaps a valid target, applies damage and removes the projectile.
     * - Awards a kill to the projectile owner if the target dies.
     * - Uses Iterator for safe removal during iteration.
     */
    private void updateProjectiles(double deltaSeconds) {
        Iterator<ServerProjectile> iter = projectiles.iterator();
        while (iter.hasNext()) {
            ServerProjectile projectile = iter.next();
            projectile.update(deltaSeconds);
            if (!projectile.isActive()) {
                iter.remove();
                continue;
            }
            for (ServerPlayer target : players.values()) {
                if (target.getId() == projectile.getOwnerId() || target.isDead()
                        || target.isInvulnerable()) {
                    continue;
                }
                if (projectile.overlaps(target.getX(), target.getY())) {
                    boolean killed = target.takeDamage(projectile.getDamage());
                    if (killed) {
                        ServerPlayer owner = players.get(projectile.getOwnerId());
                        if (owner != null) {
                            owner.addKill();
                        }
                    }
                    iter.remove();
                    break;
                }
            }
        }
    }

    /*
     * respawnReadyPlayers()
     *
     * - Checks all dead players for completed respawn timers.
     * - Respawns them at the spawn point farthest from other living players.
     */
    private void respawnReadyPlayers() {
        for (ServerPlayer player : players.values()) {
            if (player.isReadyToRespawn()) {
                double[] spawn = findBestSpawnPoint(player);
                player.respawnAt(spawn[0], spawn[1], spawn[1]);
            }
        }
    }

    /*
     * findBestSpawnPoint(ServerPlayer respawning)
     *
     * - Picks the spawn point that maximizes distance from the nearest living enemy.
     * - Iterates all spawn candidates and computes the minimum distance to other players.
     * - Returns the candidate with the largest minimum distance.
     */
    private double[] findBestSpawnPoint(ServerPlayer respawning) {
        double[] best = SPAWN_CANDIDATES[0];
        double bestMinDist = -1.0;

        for (double[] candidate : SPAWN_CANDIDATES) {
            double minDist = Double.MAX_VALUE;
            for (ServerPlayer other : players.values()) {
                if (other.getId() == respawning.getId() || other.isDead()) {
                    continue;
                }
                double dx = candidate[0] - other.getX();
                double dy = candidate[1] - other.getY();
                double dist = Math.sqrt(dx * dx + dy * dy);
                minDist = Math.min(minDist, dist);
            }
            if (minDist > bestMinDist) {
                bestMinDist = minDist;
                best = candidate;
            }
        }

        return best;
    }

    /*
     * createSnapshot()
     *
     * - Builds a GameStateSnapshot with all player states, the game timer, and the game-over flag.
     * - Each player's state is converted to a PlayerSnapshot using ServerPlayer.toSnapshot().
     * - This snapshot is serialized to JSON and sent to all clients each tick.
     */
    private GameStateSnapshot createSnapshot() {
        List<PlayerSnapshot> snapshots = new ArrayList<>();
        for (ServerPlayer player : players.values()) {
            snapshots.add(player.toSnapshot());
        }
        return new GameStateSnapshot(tick, snapshots, gameSecondsRemaining, phase == Phase.GAME_OVER);
    }

    /*
     * removeClient(int playerId)
     *
     * - Removes a disconnected client and their player from the server.
     * - Closes the client's socket connection.
     * - If still in the lobby, rechecks countdown state and broadcasts the updated lobby.
     */
    private void removeClient(int playerId) {
        synchronized (lock) {
            ClientConnection removed = clients.remove(playerId);
            players.remove(playerId);
            if (removed != null) {
                removed.close();
            }
        }
        System.out.println("Player " + playerId + " disconnected");
        if (phase == Phase.LOBBY) {
            checkCountdownState();
            broadcastLobbyState();
        }
    }

    /*
     * createPlatforms()
     *
     * - Creates the server-side platform layout for the game map.
     * - Includes one ground platform and six floating platforms.
     * - Must stay in sync with the client-side platform layout in GamePanel.
     */
    private List<ServerPlatform> createPlatforms() {
        List<ServerPlatform> mapPlatforms = new ArrayList<>();
        mapPlatforms.add(new ServerPlatform(0, SCREEN_HEIGHT - 96, 1280, 96, false));
        mapPlatforms.add(new ServerPlatform(144, 492, 224, 24, true));
        mapPlatforms.add(new ServerPlatform(456, 392, 224, 24, true));
        mapPlatforms.add(new ServerPlatform(768, 492, 224, 24, true));
        mapPlatforms.add(new ServerPlatform(296, 292, 224, 24, true));
        mapPlatforms.add(new ServerPlatform(608, 292, 224, 24, true));
        mapPlatforms.add(new ServerPlatform(1000, 392, 224, 24, true));
        return mapPlatforms;
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(1L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    /*
     * ClientConnection
     *
     * - Represents a single connected client on the server.
     * - Holds the player ID, socket, and output writer for sending messages.
     * - Tracks the client's character selection and ready status during the lobby.
     */
    private final class ClientConnection {
        private final int playerId;
        private final Socket socket;
        private final PrintWriter writer;
        int characterIndex;
        boolean ready;

        private ClientConnection(int playerId, Socket socket) throws IOException {
            this.playerId = playerId;
            this.socket = socket;
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        /*
         * startReader()
         *
         * - Starts a daemon thread to read messages from this client.
         * - The thread runs readMessages() which loops until the connection closes.
         */
        private void startReader() {
            Thread readerThread = new Thread(this::readMessages, "engkanto-client-" + playerId);
            readerThread.setDaemon(true);
            readerThread.start();
        }

        /*
         * readMessages()
         *
         * - Reads lines from the client socket in a loop.
         * - Each line is a JSON message passed to handleClientMessage for processing.
         * - When the connection closes or errors out, removes the client from the server.
         */
        private void readMessages() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleClientMessage(line);
                }
            } catch (IOException exception) {
                // Disconnection is handled below.
            } finally {
                removeClient(playerId);
            }
        }

        /*
         * handleClientMessage(String line)
         *
         * - Parses a JSON message from the client and processes it based on its type.
         * - "select_character": updates the client's character choice during the lobby.
         * - "ready": toggles the client's ready status and rechecks the countdown.
         * - "input": applies the player's input to their ServerPlayer during gameplay.
         * - "chat": broadcasts the chat message to all connected clients.
         */
        private void handleClientMessage(String line) {
            try {
                ClientMessage message = gson.fromJson(line, ClientMessage.class);
                if (message == null) {
                    return;
                }
                if ("select_character".equals(message.type)) {
                    synchronized (lock) {
                        if (phase == Phase.LOBBY && !ready) {
                            int index = message.characterIndex;
                            if (index >= 0 && index < ServerPlayer.CHARACTER_NAMES.length) {
                                characterIndex = index;
                                System.out.println("Player " + playerId + " selected " + ServerPlayer.CHARACTER_NAMES[index]);
                            }
                        }
                    }
                    broadcastLobbyState();
                } else if ("ready".equals(message.type)) {
                    synchronized (lock) {
                        if (phase == Phase.LOBBY) {
                            ready = !ready;
                            System.out.println("Player " + playerId + " ready=" + ready);
                        }
                    }
                    checkCountdownState();
                    broadcastLobbyState();
                } else if ("input".equals(message.type) && message.input != null) {
                    synchronized (lock) {
                        ServerPlayer player = phase == Phase.IN_GAME ? players.get(playerId) : null;
                        if (player != null) {
                            player.setInput(message.input);
                        }
                    }
                } else if ("chat".equals(message.type) && message.chatText != null) {
                    broadcast(ServerMessage.chat(playerId, message.chatText));
                }
            } catch (JsonSyntaxException exception) {
                System.err.println("Bad message from player " + playerId + ": " + exception.getMessage());
            }
        }

        /*
         * send(ServerMessage message)
         *
         * - Serializes a ServerMessage to JSON and sends it to this client.
         */
        private void send(ServerMessage message) {
            writer.println(gson.toJson(message));
        }

        /*
         * close()
         *
         * - Closes this client's socket connection.
         */
        private void close() {
            try {
                socket.close();
            } catch (IOException exception) {
                // Socket is already closing.
            }
        }
    }

    /*
     * broadcast(ServerMessage message)
     *
     * - Sends a server message to all connected clients.
     * - Takes a snapshot of the client list under the lock to avoid concurrent modification.
     */
    private void broadcast(ServerMessage message) {
        List<ClientConnection> connections;
        synchronized (lock) {
            connections = new ArrayList<>(clients.values());
        }
        for (ClientConnection connection : connections) {
            connection.send(message);
        }
    }
}
