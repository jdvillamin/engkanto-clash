package com.engkanto.client.game.entity;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.engkanto.client.game.GameConfig;
import com.engkanto.client.game.character.AswangCharacter;
import com.engkanto.client.game.character.CharacterDefinition;
import com.engkanto.client.game.character.EngkantoCharacter;
import com.engkanto.client.game.character.KapreCharacter;
import com.engkanto.client.game.character.PlayerAction;
import com.engkanto.client.game.character.TikbalangCharacter;
import com.engkanto.client.render.AssetLoader;
import com.engkanto.client.render.SpriteSheet;
import com.engkanto.common.model.GameStateSnapshot;
import com.engkanto.common.model.PlayerSnapshot;

public final class RemotePlayerRenderer {
    private static final int SIZE = 96;
    private static final int HEALTH_BAR_WIDTH = 78;
    private static final int HEALTH_BAR_HEIGHT = 8;

    private final CharacterDefinition[] characters = {
            new TikbalangCharacter(),
            new KapreCharacter(),
            new AswangCharacter(),
            new EngkantoCharacter()
    };

    private final Map<Integer, PlayerEffectState> effectStates = new HashMap<>();
    private final List<VisualProjectile> visualProjectiles = new ArrayList<>();
    private final List<VisualVine> visualVines = new ArrayList<>();

    private final BufferedImage kapreLogImage;
    private final BufferedImage engkantoMove1Image;
    private final BufferedImage engkantoMove2Image;
    private final BufferedImage engkantoVineImage;
    private final BufferedImage engkantoSpecialImage;

    public RemotePlayerRenderer() {
        BufferedImage kapreSheet = SpriteSheet.removeWhiteBackground(
                AssetLoader.loadImage("/assets/sprites/kapre.png"));
        kapreLogImage = kapreSheet.getSubimage(610, 1035, 150, 150);

        BufferedImage engkantoSheet = SpriteSheet.removeWhiteBackground(
                AssetLoader.loadImage("/assets/sprites/engkanto.png"));
        engkantoMove1Image = engkantoSheet.getSubimage(606, 556, 130, 130);
        engkantoMove2Image = engkantoSheet.getSubimage(550, 770, 130, 130);

        int frameWidth = engkantoSheet.getWidth() / 4;
        int frameHeight = engkantoSheet.getHeight() / 7;
        engkantoVineImage = engkantoSheet.getSubimage(
                2 * frameWidth, 4 * frameHeight, frameWidth, frameHeight);
        engkantoSpecialImage = engkantoSheet.getSubimage(
                2 * frameWidth, 5 * frameHeight, frameWidth, frameHeight);
    }

    public void update(double deltaSeconds, GameStateSnapshot state) {
        if (state == null) {
            return;
        }

        Set<Integer> activeIds = new HashSet<>();
        for (PlayerSnapshot snapshot : state.players) {
            activeIds.add(snapshot.id);
            updatePlayerEffects(snapshot);
        }
        effectStates.keySet().removeIf(id -> !activeIds.contains(id));

        Iterator<VisualProjectile> projIter = visualProjectiles.iterator();
        while (projIter.hasNext()) {
            VisualProjectile proj = projIter.next();
            proj.update(deltaSeconds);
            if (!proj.active) {
                projIter.remove();
                continue;
            }
            for (PlayerSnapshot player : state.players) {
                if (player.id == proj.ownerId || player.dead) {
                    continue;
                }
                if (proj.x + proj.drawSize > player.x
                        && proj.x < player.x + SIZE
                        && proj.y + proj.drawSize > player.y
                        && proj.y < player.y + SIZE) {
                    proj.active = false;
                    projIter.remove();
                    break;
                }
            }
        }

        Iterator<VisualVine> vineIter = visualVines.iterator();
        while (vineIter.hasNext()) {
            VisualVine vine = vineIter.next();
            vine.update(deltaSeconds);
            if (!vine.active) {
                vineIter.remove();
            }
        }
    }

    public void draw(Graphics2D graphics, PlayerSnapshot player, boolean localPlayer) {
        CharacterDefinition character = characters[clamp(player.characterIndex, 0, characters.length - 1)];
        PlayerAction action = parseAction(player.action);
        BufferedImage frame = character.getFrame(action, clamp(player.frameIndex, 0, action.getFrameCount() - 1));
        int drawX = (int) Math.round(player.x);
        int drawY = (int) Math.round(player.y + getScaledBottomPadding(frame));

        Composite original = null;
        if (player.invulnerable) {
            boolean dim = (System.currentTimeMillis() / 150) % 2 == 0;
            if (dim) {
                original = graphics.getComposite();
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            }
        }

        if (player.facingLeft) {
            graphics.drawImage(frame, drawX + SIZE, drawY, drawX, drawY + SIZE,
                    0, 0, frame.getWidth(), frame.getHeight(), null);
        } else {
            graphics.drawImage(frame, drawX, drawY, drawX + SIZE, drawY + SIZE,
                    0, 0, frame.getWidth(), frame.getHeight(), null);
        }

        if (original != null) {
            graphics.setComposite(original);
        }

        drawPlayerHud(graphics, player, localPlayer);
    }

    public void drawEffects(Graphics2D graphics) {
        for (VisualVine vine : visualVines) {
            vine.draw(graphics);
        }
        for (VisualProjectile proj : visualProjectiles) {
            proj.draw(graphics);
        }
    }

    private void drawPlayerHud(Graphics2D graphics, PlayerSnapshot player, boolean localPlayer) {
        int centerX = (int) Math.round(player.x + SIZE / 2.0);
        int labelY = (int) Math.round(player.y - 28.0);
        String label = localPlayer ? "You" : "P" + player.id;

        FontMetrics metrics = graphics.getFontMetrics();
        int labelWidth = metrics.stringWidth(label);
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRoundRect(centerX - labelWidth / 2 - 6, labelY - metrics.getAscent(),
                labelWidth + 12, metrics.getHeight(), 8, 8);
        graphics.setColor(localPlayer ? new Color(145, 231, 255) : Color.WHITE);
        graphics.drawString(label, centerX - labelWidth / 2, labelY);

        int healthX = centerX - HEALTH_BAR_WIDTH / 2;
        int healthY = (int) Math.round(player.y - 18.0);
        double healthPercent = player.maxHealth <= 0.0 ? 0.0
                : Math.max(0.0, Math.min(1.0, player.health / player.maxHealth));
        graphics.setColor(new Color(0, 0, 0, 170));
        graphics.fillRect(healthX - 1, healthY - 1, HEALTH_BAR_WIDTH + 2, HEALTH_BAR_HEIGHT + 2);
        graphics.setColor(new Color(84, 34, 42));
        graphics.fillRect(healthX, healthY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
        graphics.setColor(player.dead ? new Color(126, 126, 126) : new Color(83, 218, 112));
        graphics.fillRect(healthX, healthY,
                (int) Math.round(HEALTH_BAR_WIDTH * healthPercent), HEALTH_BAR_HEIGHT);
    }

    private void updatePlayerEffects(PlayerSnapshot snapshot) {
        PlayerEffectState state = effectStates.computeIfAbsent(
                snapshot.id, k -> new PlayerEffectState());

        String action = snapshot.action;
        int frame = snapshot.frameIndex;

        boolean newAction = action == null || !action.equals(state.lastAction);
        boolean frameReset = !newAction && frame < state.lastFrameIndex;
        if (newAction || frameReset) {
            state.effectSpawned = false;
        }

        if (!state.effectSpawned && frame >= 2) {
            spawnVisualEffect(snapshot, action);
            state.effectSpawned = true;
        }

        state.lastAction = action;
        state.lastFrameIndex = frame;
    }

    private void spawnVisualEffect(PlayerSnapshot snapshot, String action) {
        if (action == null) {
            return;
        }
        int charIndex = snapshot.characterIndex;
        boolean left = snapshot.facingLeft;
        int direction = left ? -1 : 1;

        if (charIndex == 1 && "MOVE_3".equals(action)) {
            double px = left ? snapshot.x - 24.0 : snapshot.x + SIZE - 24.0;
            double py = snapshot.y + SIZE - 42.0;
            visualProjectiles.add(new VisualProjectile(kapreLogImage, px, py, direction, 48, snapshot.id));
        } else if (charIndex == 3) {
            spawnEngkantoEffect(snapshot, action, direction);
        }
    }

    private void spawnEngkantoEffect(PlayerSnapshot snapshot, String action, int direction) {
        boolean left = snapshot.facingLeft;
        switch (action) {
            case "MOVE_1" -> {
                double px = left ? snapshot.x - 24.0 : snapshot.x + SIZE - 24.0;
                double py = snapshot.y + SIZE - 58.0;
                visualProjectiles.add(new VisualProjectile(engkantoMove1Image, px, py, direction, 48, snapshot.id));
            }
            case "MOVE_2" -> {
                double px = left ? snapshot.x - 24.0 : snapshot.x + SIZE - 24.0;
                double py = snapshot.y + SIZE - 58.0;
                visualProjectiles.add(new VisualProjectile(engkantoMove2Image, px, py, direction, 48, snapshot.id));
            }
            case "MOVE_3" -> {
                double vx = left ? snapshot.x - 96 : snapshot.x + SIZE;
                visualVines.add(new VisualVine(engkantoVineImage, vx, snapshot.y, left, 0.55));
            }
            case "SPECIAL" -> {
                double px = left ? snapshot.x - 128 + 24.0 : snapshot.x + SIZE - 24.0;
                double py = snapshot.y + SIZE - 128 + 12.0;
                visualProjectiles.add(new VisualProjectile(engkantoSpecialImage, px, py, direction, 128, snapshot.id));
            }
            default -> { }
        }
    }

    private PlayerAction parseAction(String action) {
        if (action == null) {
            return PlayerAction.IDLE;
        }
        try {
            return PlayerAction.valueOf(action);
        } catch (IllegalArgumentException exception) {
            return PlayerAction.IDLE;
        }
    }

    private double getScaledBottomPadding(BufferedImage frame) {
        return getBottomPadding(frame) * SIZE / (double) frame.getHeight();
    }

    private int getBottomPadding(BufferedImage frame) {
        for (int row = frame.getHeight() - 1; row >= 0; row--) {
            for (int column = 0; column < frame.getWidth(); column++) {
                int alpha = (frame.getRGB(column, row) >>> 24) & 0xFF;
                if (alpha != 0) {
                    return frame.getHeight() - 1 - row;
                }
            }
        }
        return 0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static final class PlayerEffectState {
        String lastAction = "IDLE";
        int lastFrameIndex;
        boolean effectSpawned;
    }

    private static final class VisualProjectile {
        private static final double SPEED = 360.0;
        private final BufferedImage image;
        private final int direction;
        private final int drawSize;
        private final int ownerId;
        private final double y;
        private double x;
        private boolean active = true;

        VisualProjectile(BufferedImage image, double x, double y, int direction, int drawSize, int ownerId) {
            this.image = image;
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.drawSize = drawSize;
            this.ownerId = ownerId;
        }

        void update(double deltaSeconds) {
            x += direction * SPEED * deltaSeconds;
            active = x + drawSize >= 0.0 && x <= GameConfig.SCREEN_WIDTH;
        }

        void draw(Graphics2D graphics) {
            if (direction < 0) {
                graphics.drawImage(image, (int) x + drawSize, (int) y, -drawSize, drawSize, null);
            } else {
                graphics.drawImage(image, (int) x, (int) y, drawSize, drawSize, null);
            }
        }
    }

    private static final class VisualVine {
        private static final int DRAW_SIZE = 96;
        private final BufferedImage image;
        private final double x;
        private final double y;
        private final boolean facingLeft;
        private double remaining;
        private boolean active = true;

        VisualVine(BufferedImage image, double x, double y, boolean facingLeft, double seconds) {
            this.image = image;
            this.x = x;
            this.y = y;
            this.facingLeft = facingLeft;
            this.remaining = seconds;
        }

        void update(double deltaSeconds) {
            remaining -= deltaSeconds;
            active = remaining > 0.0;
        }

        void draw(Graphics2D graphics) {
            if (facingLeft) {
                graphics.drawImage(image, (int) x + DRAW_SIZE, (int) y, -DRAW_SIZE, DRAW_SIZE, null);
            } else {
                graphics.drawImage(image, (int) x, (int) y, DRAW_SIZE, DRAW_SIZE, null);
            }
        }
    }
}
