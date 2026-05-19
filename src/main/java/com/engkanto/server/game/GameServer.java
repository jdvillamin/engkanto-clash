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

    public GameServer(int port) {
        this.port = port;
    }

    public void runBlocking() throws IOException {
        running = true;
        serverSocket = new ServerSocket(port);
        Thread acceptThread = new Thread(this::acceptClients, "engkanto-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        System.out.println("Engkanto Clash server listening on port " + port);
        runGameLoop();
    }

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

    private void update(double deltaSeconds) {
        if (phase == Phase.LOBBY) {
            updateLobby(deltaSeconds);
            return;
        }
        if (phase == Phase.GAME_OVER) {
            broadcastGameState();
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

    private void respawnReadyPlayers() {
        for (ServerPlayer player : players.values()) {
            if (player.isReadyToRespawn()) {
                double[] spawn = findBestSpawnPoint(player);
                player.respawnAt(spawn[0], spawn[1], spawn[1]);
            }
        }
    }

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

    private GameStateSnapshot createSnapshot() {
        List<PlayerSnapshot> snapshots = new ArrayList<>();
        for (ServerPlayer player : players.values()) {
            snapshots.add(player.toSnapshot());
        }
        return new GameStateSnapshot(tick, snapshots, gameSecondsRemaining, phase == Phase.GAME_OVER);
    }

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

        private void startReader() {
            Thread readerThread = new Thread(this::readMessages, "engkanto-client-" + playerId);
            readerThread.setDaemon(true);
            readerThread.start();
        }

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

        private void send(ServerMessage message) {
            writer.println(gson.toJson(message));
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException exception) {
                // Socket is already closing.
            }
        }
    }

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
