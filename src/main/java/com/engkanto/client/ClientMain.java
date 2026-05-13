package com.engkanto.client;

import javax.swing.SwingUtilities;

import com.engkanto.client.net.NetworkClient;
import com.engkanto.common.net.NetworkConfig;

public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        NetworkClient networkClient = connectIfAvailable(args);
        SwingUtilities.invokeLater(() -> {
            GameWindow window = new GameWindow(networkClient);
            window.show();
        });
    }

    private static NetworkClient connectIfAvailable(String[] args) {
        ClientOptions options = ClientOptions.from(args);
        if (options.offline) {
            System.out.println("Starting Engkanto Clash in local mode");
            return null;
        }
        try {
            NetworkClient client = NetworkClient.connect(options.host, options.port);
            System.out.println("Connected to Engkanto Clash server at " + options.host + ":" + options.port);
            return client;
        } catch (Exception exception) {
            System.out.println("No server at " + options.host + ":" + options.port + "; starting local mode");
            return null;
        }
    }

    private record ClientOptions(String host, int port, boolean offline) {
        private static ClientOptions from(String[] args) {
            String host = NetworkConfig.DEFAULT_HOST;
            int port = NetworkConfig.DEFAULT_PORT;
            boolean offline = false;

            for (String arg : args) {
                if ("--offline".equals(arg)) {
                    offline = true;
                } else if (arg.startsWith("--host=")) {
                    host = arg.substring("--host=".length());
                } else if (arg.startsWith("--port=")) {
                    port = parsePort(arg.substring("--port=".length()));
                }
            }
            return new ClientOptions(host, port, offline);
        }

        private static int parsePort(String rawPort) {
            try {
                return Integer.parseInt(rawPort);
            } catch (NumberFormatException exception) {
                return NetworkConfig.DEFAULT_PORT;
            }
        }
    }
}
