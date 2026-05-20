package com.engkanto.client.net;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.engkanto.common.model.GameStateSnapshot;
import com.engkanto.common.model.LobbySnapshot;
import com.engkanto.common.model.PlayerInputSnapshot;
import com.engkanto.common.net.ClientMessage;
import com.engkanto.common.net.ServerMessage;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public final class NetworkClient implements Closeable {
    private static final int CONNECT_TIMEOUT_MILLIS = 1_500;

    private final Gson gson = new Gson();
    private final Socket socket;
    private final PrintWriter writer;
    private final AtomicReference<GameStateSnapshot> latestState = new AtomicReference<>();
    private final AtomicReference<LobbySnapshot> latestLobbyState = new AtomicReference<>();
    private final CopyOnWriteArrayList<ChatMessage> chatMessages = new CopyOnWriteArrayList<>();

    private volatile boolean connected = true;
    private volatile int localPlayerId;
    private volatile boolean gameStarted;

    private NetworkClient(Socket socket) throws IOException {
        this.socket = socket;
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    public static NetworkClient connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        NetworkClient client = new NetworkClient(socket);
        client.startReader();
        return client;
    }

    public boolean isConnected() {
        return connected && !socket.isClosed();
    }

    public int getLocalPlayerId() {
        return localPlayerId;
    }

    public GameStateSnapshot getLatestState() {
        return latestState.get();
    }

    public LobbySnapshot getLatestLobbyState() {
        return latestLobbyState.get();
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public List<ChatMessage> getChatMessages() {
        return chatMessages;
    }

    public void sendCharacterSelect(int characterIndex) {
        if (!isConnected()) {
            return;
        }
        writer.println(gson.toJson(ClientMessage.selectCharacter(characterIndex)));
        if (writer.checkError()) {
            connected = false;
        }
    }

    public void sendReady() {
        if (!isConnected()) {
            return;
        }
        writer.println(gson.toJson(ClientMessage.ready()));
        if (writer.checkError()) {
            connected = false;
        }
    }

    public void sendInput(PlayerInputSnapshot input) {
        if (!isConnected()) {
            return;
        }
        writer.println(gson.toJson(ClientMessage.input(input)));
        if (writer.checkError()) {
            connected = false;
        }
    }

    public void sendChat(String text) {
        if (!isConnected() || text == null || text.isBlank()) {
            return;
        }
        writer.println(gson.toJson(ClientMessage.chat(text)));
        if (writer.checkError()) {
            connected = false;
        }
    }

    public void sendExitToLobby() {
        if (!isConnected()) return;
        writer.println(gson.toJson(ClientMessage.exitToLobby()));
        if (writer.checkError()) connected = false;
    }

    @Override
    public void close() {
        connected = false;
        try {
            socket.close();
        } catch (IOException exception) {
            
        }
    }

    public static final class ChatMessage {
        public final int senderId;
        public final String text;
        public final long timestampMillis;

        private ChatMessage(int senderId, String text) {
            this.senderId = senderId;
            this.text = text;
            this.timestampMillis = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return "Player " + senderId + ": " + text;
        }
    }

    private void startReader() {
        Thread readerThread = new Thread(this::readMessages, "engkanto-network-client");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readMessages() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleServerMessage(line);
            }
        } catch (IOException exception) {
        } finally {
            connected = false;
        }
    }

    private void handleServerMessage(String line) {
        try {
            ServerMessage message = gson.fromJson(line, ServerMessage.class);
            if (message == null) {
                return;
            }
            if ("welcome".equals(message.type)) {
                localPlayerId = message.playerId;
            } else if ("lobby_state".equals(message.type) && message.lobbyState != null) {
                latestLobbyState.set(message.lobbyState);
            } else if ("game_start".equals(message.type)) {
                gameStarted = true;
            } else if ("state".equals(message.type) && message.state != null) {
                latestState.set(message.state);
            } else if ("chat".equals(message.type) && message.chatText != null) {
                chatMessages.add(new ChatMessage(message.playerId, message.chatText));
            } else if ("disconnect".equals(message.type)) {
                connected = false;
                close();
            }
        } catch (JsonSyntaxException exception) {
            connected = false;
        }
    }
}