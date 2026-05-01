package com.engkanto.client.game;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import com.engkanto.client.game.character.PlayerAction;
import com.engkanto.client.game.combat.AbilityUI;
import com.engkanto.client.game.combat.HealthUI;
import com.engkanto.client.game.entity.Player;
import com.engkanto.client.game.entity.Projectile;
import com.engkanto.client.game.entity.TestDummy;
import com.engkanto.client.game.world.Platform;
import com.engkanto.client.input.KeyboardInput;
import com.engkanto.client.render.DebugRenderer;

public final class GamePanel extends JPanel implements Runnable {
    private final KeyboardInput keyboardInput;
    private final List<Platform> platforms;
    private final Player player;
    private final TestDummy dummy;
    private final DebugRenderer debugRenderer;
    private final HealthUI healthUI;
    private final AbilityUI abilityUI;

    private Thread gameThread;
    private boolean running;
    private PlayerAction activeDirectAttack;
    private boolean directAttackHitApplied;

    public GamePanel() {
        keyboardInput = new KeyboardInput();
        platforms = createPlatforms();
        player = new Player(
                GameConfig.SCREEN_WIDTH / 2.0 - Player.SIZE / 2.0,
                getGroundPlatformTop() - Player.SIZE
        );
        dummy = new TestDummy(700, getGroundPlatformTop() - TestDummy.HEIGHT);

        debugRenderer = new DebugRenderer();
        healthUI = new HealthUI(player);
        abilityUI = new AbilityUI(player);

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
        if (keyboardInput.consumeDamageRequested()) {
            player.takeDamage(25.0);
        }
        if (keyboardInput.consumeHealRequested()) {
            player.heal(25.0);
        }
        player.update(keyboardInput, platforms, deltaSeconds);
        dummy.update(deltaSeconds);
        resolvePlayerAttacks();
        resolveProjectileHits();
    }

    private void resolvePlayerAttacks() {
        if (player.isDead() || dummy.getHealthComponent().isDead()) {
            resetDirectAttackTracking();
            return;
        }

        PlayerAction action = player.getCurrentAction();
        if (!isDirectAttack(action) || !player.isActionLocked()) {
            resetDirectAttackTracking();
            return;
        }

        if (activeDirectAttack != action) {
            activeDirectAttack = action;
            directAttackHitApplied = false;
        }
        if (directAttackHitApplied) {
            return;
        }
        if (!player.canActiveDirectAttackHit()) {
            return;
        }

        boolean overlaps = dummy.overlapsHitbox(
                player.getX(),
                player.getY(),
                player.getX() + Player.SIZE,
                player.getY() + Player.SIZE
        );

        if (overlaps) {
            directAttackHitApplied = player.applyActiveDirectAttack(dummy.getHealthComponent());
        }
    }

    private boolean isDirectAttack(PlayerAction action) {
        return action == PlayerAction.MOVE_1
                || action == PlayerAction.MOVE_2
                || action == PlayerAction.MOVE_3
                || action == PlayerAction.SPECIAL;
    }

    private void resetDirectAttackTracking() {
        activeDirectAttack = null;
        directAttackHitApplied = false;
    }

    private void resolveProjectileHits() {
        for (Projectile projectile : player.getActiveCharacterProjectiles()) {
            if (!projectile.isActive()) continue;
            boolean overlaps = dummy.overlapsHitbox(
                    projectile.getX(),
                    projectile.getY(),
                    projectile.getX() + projectile.getDrawSize(),
                    projectile.getY() + projectile.getDrawSize()
            );
            if (overlaps) {
                projectile.hit(dummy.getHealthComponent());
            }
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            drawWorld(graphics2D);
            drawPlatforms(graphics2D);
            player.draw(graphics2D);
            dummy.draw(graphics2D);
            healthUI.draw(graphics2D);
            abilityUI.draw(graphics2D);
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
        mapPlatforms.add(new Platform(0, GameConfig.SCREEN_HEIGHT - 96, GameConfig.SCREEN_WIDTH, 96, Platform.Type.GROUND));
        mapPlatforms.add(new Platform(144, 492, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(456, 392, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(768, 492, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(296, 292, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(608, 292, 224, 24, Platform.Type.FLOATING));
        mapPlatforms.add(new Platform(1000, 392, 224, 24, Platform.Type.FLOATING));
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
