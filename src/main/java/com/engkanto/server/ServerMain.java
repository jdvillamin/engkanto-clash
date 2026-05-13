package com.engkanto.server;

import java.io.IOException;

import com.engkanto.common.net.NetworkConfig;
import com.engkanto.server.game.GameServer;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) {
        int port = parsePort(args);
        try {
            new GameServer(port).runBlocking();
        } catch (IOException exception) {
            System.err.println("Engkanto Clash server stopped: " + exception.getMessage());
        }
    }

    private static int parsePort(String[] args) {
        if (args.length == 0) {
            return NetworkConfig.DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            System.err.println("Invalid port '" + args[0] + "', using " + NetworkConfig.DEFAULT_PORT);
            return NetworkConfig.DEFAULT_PORT;
        }
    }
}
