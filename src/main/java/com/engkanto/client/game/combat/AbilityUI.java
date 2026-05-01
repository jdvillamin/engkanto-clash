package com.engkanto.client.game.combat;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;

import com.engkanto.client.game.GameConfig;
import com.engkanto.client.game.character.PlayerAction;
import com.engkanto.client.game.entity.Player;

public final class AbilityUI {
    private static final int KEY_SIZE = 46;
    private static final int GAP = 10;
    private static final int Y_OFFSET = 84;

    private static final Color READY_FILL = new Color(245, 232, 184);
    private static final Color READY_BORDER = new Color(74, 52, 30);
    private static final Color COOLDOWN_OVERLAY = new Color(0, 0, 0, 160);
    private static final Color KEY_TEXT = new Color(34, 26, 18);
    private static final Color DISABLED_TEXT = new Color(185, 176, 150);

    private static final Font KEY_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    private static final Font TIMER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    private final Player player;

    public AbilityUI(Player player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    public void draw(Graphics2D graphics) {
        Objects.requireNonNull(graphics, "graphics");

        Object originalAntialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Font originalFont = graphics.getFont();
        Color originalColor = graphics.getColor();
        java.awt.Stroke originalStroke = graphics.getStroke();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int totalWidth = KEY_SIZE * 4 + GAP * 3;
            int startX = GameConfig.SCREEN_WIDTH / 2 - totalWidth / 2;
            int y = GameConfig.SCREEN_HEIGHT - Y_OFFSET;

            drawKey(graphics, startX, y, "J", PlayerAction.MOVE_1);
            drawKey(graphics, startX + KEY_SIZE + GAP, y, "K", PlayerAction.MOVE_2);
            drawKey(graphics, startX + (KEY_SIZE + GAP) * 2, y, "E", PlayerAction.MOVE_3);
            drawKey(graphics, startX + (KEY_SIZE + GAP) * 3, y, "L", PlayerAction.SPECIAL);
        } finally {
            if (originalAntialias != null) {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialias);
            }
            graphics.setStroke(originalStroke);
            graphics.setFont(originalFont);
            graphics.setColor(originalColor);
        }
    }

    private void drawKey(Graphics2D graphics, int x, int y, String label, PlayerAction action) {
        double duration = player.getCooldownDuration(action);
        double remaining = player.getCooldownRemaining(action);
        double fraction = duration <= 0.0 ? 0.0 : clamp(remaining / duration, 0.0, 1.0);
        boolean onCooldown = fraction > 0.0;

        graphics.setColor(READY_BORDER);
        graphics.fillRoundRect(x - 2, y - 2, KEY_SIZE + 4, KEY_SIZE + 4, 10, 10);
        graphics.setColor(READY_FILL);
        graphics.fillRoundRect(x, y, KEY_SIZE, KEY_SIZE, 8, 8);

        if (onCooldown) {
            graphics.setColor(COOLDOWN_OVERLAY);
            graphics.fillRoundRect(x, y, KEY_SIZE, KEY_SIZE, 8, 8);

            int fillHeight = (int) Math.round(KEY_SIZE * fraction);
            graphics.setColor(new Color(30, 30, 30, 120));
            graphics.fillRoundRect(x, y + KEY_SIZE - fillHeight, KEY_SIZE, fillHeight, 8, 8);

            drawTimer(graphics, x, y, remaining);
        }

        graphics.setFont(KEY_FONT);
        graphics.setColor(onCooldown ? DISABLED_TEXT : KEY_TEXT);
        FontMetrics metrics = graphics.getFontMetrics();
        int textX = x + KEY_SIZE / 2 - metrics.stringWidth(label) / 2;
        int textY = y + KEY_SIZE / 2 + (metrics.getAscent() - metrics.getDescent()) / 2;
        graphics.drawString(label, textX, textY);
    }

    private void drawTimer(Graphics2D graphics, int x, int y, double remaining) {
        String text = String.format("%.1f", remaining);
        graphics.setFont(TIMER_FONT);
        graphics.setColor(Color.WHITE);
        FontMetrics metrics = graphics.getFontMetrics();
        int textX = x + KEY_SIZE / 2 - metrics.stringWidth(text) / 2;
        int textY = y + KEY_SIZE - 6;
        graphics.drawString(text, textX, textY);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
