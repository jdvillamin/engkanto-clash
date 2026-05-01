package com.engkanto.client.game;

import com.engkanto.client.game.entity.Player;
import com.engkanto.client.game.world.Platform;
import com.engkanto.client.input.KeyboardInput;
import com.engkanto.client.render.DebugRenderer;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

public final class GamePanel extends JPanel implements Runnable {
    private final KeyboardInput keyboardInput;
    private final List<Platform> platforms;
    private final Player player;
    private final DebugRenderer debugRenderer;

    private Thread gameThread;
    private boolean running;

    public GamePanel() {
        keyboardInput = new KeyboardInput();
        platforms = createPlatforms();
        player = new Player(
                GameConfig.SCREEN_WIDTH / 2.0 - Player.SIZE / 2.0,
                getGroundPlatformTop() - Player.SIZE
        );
        debugRenderer = new DebugRenderer();

        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setBackground(new Color(28, 36, 32));
        setDoubleBuffered(true);
        setFocusable(true);
        addKeyListener(keyboardInput);
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        gameThread = new Thread(this, "engkanto-game-loop");
        gameThread.start();
        requestFocusInWindow();
    }

    @Override
    public void run() {
        final double secondsPerUpdate = 1.0 / GameConfig.TARGET_UPDATES_PER_SECOND;
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

            repaint();
            sleepBriefly();
        }
    }

    private void update(double deltaSeconds) {
        player.update(keyboardInput, platforms, deltaSeconds);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            drawWorld(graphics2D);
            drawPlatforms(graphics2D);
            player.draw(graphics2D);
            debugRenderer.drawHud(graphics2D);
        } finally {
            graphics2D.dispose();
        }
    }

    private void drawWorld(Graphics2D graphics) {
        graphics.setColor(new Color(42, 92, 76));
        graphics.fillRect(0, 0, getWidth(), getHeight());

        graphics.setColor(new Color(34, 74, 62));
        for (int x = 0; x < getWidth(); x += GameConfig.TILE_SIZE) {
            graphics.drawLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += GameConfig.TILE_SIZE) {
            graphics.drawLine(0, y, getWidth(), y);
        }
    }

    private void drawPlatforms(Graphics2D graphics) {
        for (Platform platform : platforms) {
            platform.draw(graphics);
        }
    }

    private List<Platform> createPlatforms() {
        List<Platform> mapPlatforms = new ArrayList<>();
        mapPlatforms.add(new Platform(0, 480, GameConfig.SCREEN_WIDTH, 96, Platform.Type.GROUND));
        mapPlatforms.add(new Platform(96, 350, 184, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(520, 350, 184, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(308, 220, 184, 24, Platform.Type.FLOATING));
        return mapPlatforms;
    }

    private double getGroundPlatformTop() {
        return platforms.get(0).getTop();
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(1L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
