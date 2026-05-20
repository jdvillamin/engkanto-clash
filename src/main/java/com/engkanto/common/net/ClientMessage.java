/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file defines messages sent from the client to the server. It supports player input, character selection, ready status, and chat.
 */
package com.engkanto.common.net;

import com.engkanto.common.model.PlayerInputSnapshot;

public final class ClientMessage {
    public String type;
    public PlayerInputSnapshot input;
    public String chatText;
    public int characterIndex;

    public ClientMessage() {
    }

    private ClientMessage(String type) {
        this.type = type;
    }

    public static ClientMessage input(PlayerInputSnapshot input) {
        ClientMessage message = new ClientMessage("input");
        message.input = input;
        return message;
    }

    public static ClientMessage chat(String chatText) {
        ClientMessage message = new ClientMessage("chat");
        message.chatText = chatText;
        return message;
    }

    public static ClientMessage selectCharacter(int characterIndex) {
        ClientMessage message = new ClientMessage("select_character");
        message.characterIndex = characterIndex;
        return message;
    }

    public static ClientMessage ready() {
        return new ClientMessage("ready");
    }
}
