package com.engkanto.common.net;

import com.engkanto.common.model.GameStateSnapshot;
import com.engkanto.common.model.LobbySnapshot;

public final class ServerMessage {
    public String type;
    public int playerId;
    public GameStateSnapshot state;
    public LobbySnapshot lobbyState;
    public String chatText;

    public ServerMessage() {
    }

    private ServerMessage(String type) {
        this.type = type;
    }

    public static ServerMessage welcome(int playerId) {
        ServerMessage message = new ServerMessage("welcome");
        message.playerId = playerId;
        return message;
    }

    public static ServerMessage gameState(GameStateSnapshot state) {
        ServerMessage message = new ServerMessage("state");
        message.state = state;
        return message;
    }

    public static ServerMessage lobbyState(LobbySnapshot lobbyState) {
        ServerMessage message = new ServerMessage("lobby_state");
        message.lobbyState = lobbyState;
        return message;
    }

    public static ServerMessage gameStart() {
        return new ServerMessage("game_start");
    }

    public static ServerMessage chat(int playerId, String chatText) {
        ServerMessage message = new ServerMessage("chat");
        message.playerId = playerId;
        message.chatText = chatText;
        return message;
    }

    public static ServerMessage disconnect(String reason) {
        ServerMessage message = new ServerMessage("disconnect");
        message.chatText = reason;
        return message;
    }
}
