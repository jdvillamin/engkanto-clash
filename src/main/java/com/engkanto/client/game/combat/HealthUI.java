/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file draws the player health display. It shows the current health bar and health text during gameplay.
 */
package com.engkanto.client.game.combat;

import com.engkanto.client.game.entity.Player;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Objects;

public class HealthUI {
    private static final int X = 18;
    private static final int Y = 46;
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 18;
    private static final Color BAR_BACKGROUND = new Color(84, 34, 42);
    private static final Color HEALTH_HIGH = new Color(83, 218, 112);
    private static final Color HEALTH_MEDIUM = new Color(255, 200, 50);
    private static final Color HEALTH_LOW = new Color(230, 70, 70);
    private static final Color HEALTH_DEAD = new Color(126, 126, 126);

    private final Player player;
    private final HealthComponent health;
    private final Font labelFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private final Font hpFont = new Font(Font.SANS_SERIF, Font.BOLD, 12);

    public HealthUI(Player player) {
        this.player = Objects.requireNonNull(player, "player");
        this.health = Objects.requireNonNull(player.getHealthComponent(), "health");
    }

    public void draw(Graphics2D graphics) {
        Objects.requireNonNull(graphics, "graphics");

        Font originalFont = graphics.getFont();
        Color originalColor = graphics.getColor();
        try {
            graphics.setColor(new Color(0, 0, 0, 140));
            graphics.fillRoundRect(16, 12, 280, 26, 8, 8);
            graphics.setColor(Color.WHITE);
            graphics.setFont(labelFont);
            graphics.drawString("You — " + player.getCharacterName(), 26, 30);

            double healthPercent = clamp(health.getHealthPercentage(), 0.0, 1.0);

            graphics.setColor(Color.BLACK);
            graphics.fillRoundRect(X - 2, Y - 2, BAR_WIDTH + 4, BAR_HEIGHT + 4, 8, 8);
            graphics.setColor(BAR_BACKGROUND);
            graphics.fillRoundRect(X, Y, BAR_WIDTH, BAR_HEIGHT, 6, 6);

            graphics.setColor(getHealthColor(healthPercent));
            int healthWidth = (int) Math.round(BAR_WIDTH * healthPercent);
            graphics.fillRoundRect(X, Y, healthWidth, BAR_HEIGHT, 6, 6);

            graphics.setColor(Color.WHITE);
            graphics.setFont(hpFont);
            String healthText = String.format("%.0f / %.0f",
                    health.getCurrentHealth(), health.getMaxHealth());
            graphics.drawString(healthText, X + BAR_WIDTH + 8, Y + 14);
        } finally {
            graphics.setFont(originalFont);
            graphics.setColor(originalColor);
        }
    }

    private Color getHealthColor(double percent) {
        if (health.isDead()) return HEALTH_DEAD;
        if (percent > 0.6) return HEALTH_HIGH;
        if (percent > 0.3) return HEALTH_MEDIUM;
        return HEALTH_LOW;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
