package com.engkanto.client;

import javax.swing.SwingUtilities;

public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameWindow window = new GameWindow();
            window.show();
        });
    }
}
