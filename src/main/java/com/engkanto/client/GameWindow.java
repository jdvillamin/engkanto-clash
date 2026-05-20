package com.engkanto.client;

import com.engkanto.client.audio.AudioCue;
import com.engkanto.client.audio.AudioManager;
import com.engkanto.client.game.GamePanel;
import com.engkanto.client.lobby.LobbyPanel;
import com.engkanto.client.net.NetworkClient;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class GameWindow {
    private static final String TITLE = "Engkanto Clash";

    private final JFrame frame;
    private final NetworkClient networkClient;
    private final AudioManager audioManager;
    private LobbyPanel lobbyPanel;
    private GamePanel gamePanel;

    public GameWindow() {
        this(null);
    }

    public GameWindow(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.audioManager = AudioManager.getInstance();
        frame = new JFrame(TITLE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                audioManager.shutdown();
            }
        });

        if (networkClient != null && networkClient.isConnected()) {
            lobbyPanel = new LobbyPanel(networkClient, this::transitionToGame, audioManager);
            frame.add(lobbyPanel, BorderLayout.CENTER);
        } else {
            gamePanel = new GamePanel(networkClient, audioManager);
            frame.add(gamePanel, BorderLayout.CENTER);
        }

        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    public void show() {
        frame.setVisible(true);
        if (lobbyPanel != null) {
            audioManager.playMusic(AudioCue.MENU_MUSIC);
            lobbyPanel.start();
        } else {
            audioManager.playMusic(AudioCue.IN_GAME_MUSIC);
            gamePanel.start();
        }
    }

    private void transitionToGame() {
        if (lobbyPanel != null) {
            lobbyPanel.stop();
            frame.remove(lobbyPanel);
            lobbyPanel = null;
        }
        audioManager.playMusic(AudioCue.IN_GAME_MUSIC);
        gamePanel = new GamePanel(networkClient, audioManager);
        frame.add(gamePanel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
        gamePanel.start();
    }
}
