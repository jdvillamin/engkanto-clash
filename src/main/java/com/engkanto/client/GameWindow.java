package com.engkanto.client;

import com.engkanto.client.game.GamePanel;

import javax.swing.JFrame;
import java.awt.BorderLayout;

public final class GameWindow {
    private static final String TITLE = "Engkanto Clash";

    private final JFrame frame;
    private final GamePanel gamePanel;

    public GameWindow() {
        frame = new JFrame(TITLE);
        gamePanel = new GamePanel();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());
        frame.add(gamePanel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    public void show() {
        frame.setVisible(true);
        gamePanel.start();
    }
}
