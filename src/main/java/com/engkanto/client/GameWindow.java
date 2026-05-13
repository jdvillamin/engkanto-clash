package com.engkanto.client;

import com.engkanto.client.audio.BackgroundMusic;
import com.engkanto.client.game.GamePanel;
import com.engkanto.client.lobby.LobbyPanel;
import com.engkanto.client.net.NetworkClient;

import javax.swing.JFrame;
import java.awt.BorderLayout;

public final class GameWindow {
    private static final String TITLE = "Engkanto Clash";

    private final JFrame frame;
    private final NetworkClient networkClient;
    private LobbyPanel lobbyPanel;
    private GamePanel gamePanel;

    public GameWindow() {
        this(null);
    }

    public GameWindow(NetworkClient networkClient) {
        this.networkClient = networkClient;
        frame = new JFrame(TITLE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());

        if (networkClient != null && networkClient.isConnected()) {
            lobbyPanel = new LobbyPanel(networkClient, this::transitionToGame);
            frame.add(lobbyPanel, BorderLayout.CENTER);
        } else {
            gamePanel = new GamePanel(networkClient);
            frame.add(gamePanel, BorderLayout.CENTER);
        }

        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    public void show() {
        frame.setVisible(true);
        new BackgroundMusic().start();
        if (lobbyPanel != null) {
            lobbyPanel.start();
        } else {
            gamePanel.start();
        }
    }

    private void transitionToGame() {
        if (lobbyPanel != null) {
            lobbyPanel.stop();
            frame.remove(lobbyPanel);
            lobbyPanel = null;
        }
        gamePanel = new GamePanel(networkClient);
        frame.add(gamePanel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
        gamePanel.start();
    }
}
