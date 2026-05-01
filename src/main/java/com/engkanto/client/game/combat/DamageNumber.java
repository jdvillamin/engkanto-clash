package com.engkanto.client.game.combat;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class DamageNumber {
    private static final double LIFETIME_SECONDS    = 1.1;
    private static final double RISE_SPEED          = 52.0;
    private static final double FADE_START_FRACTION = 0.45;
    private static final Font   DAMAGE_FONT         = new Font("SansSerif", Font.BOLD, 16);

    private static final Color COLOR_NORMAL  = new Color(255, 220, 80);
    private static final Color COLOR_HEAVY   = new Color(255, 90,  60);
    private static final Color COLOR_OUTLINE = new Color(20,  10,  10);

    private double x;
    private double y;
    private final String text;
    private final Color color;
    private double elapsedSeconds;
    private boolean expired;

    public DamageNumber(double centerX, double topY, double damage) {
        this.text    = formatDamage(damage);
        this.color   = damage >= 40.0 ? COLOR_HEAVY : COLOR_NORMAL;
        this.x       = centerX;
        this.y       = topY;
    }

    public void update(double deltaSeconds) {
        if (expired) return;
        elapsedSeconds += deltaSeconds;
        y -= RISE_SPEED * deltaSeconds;
        if (elapsedSeconds >= LIFETIME_SECONDS) {
            expired = true;
        }
    }

    public void draw(Graphics2D graphics) {
        if (expired) return;

        float alpha = computeAlpha();

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setFont(DAMAGE_FONT);
        FontMetrics fm = graphics.getFontMetrics();
        int textWidth  = fm.stringWidth(text);
        int drawX      = (int) x - textWidth / 2;
        int drawY      = (int) y;

        Composite original = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        graphics.setColor(COLOR_OUTLINE);
        graphics.drawString(text, drawX - 1, drawY - 1);
        graphics.drawString(text, drawX + 1, drawY - 1);
        graphics.drawString(text, drawX - 1, drawY + 1);
        graphics.drawString(text, drawX + 1, drawY + 1);

        graphics.setColor(color);
        graphics.drawString(text, drawX, drawY);

        graphics.setComposite(original);
    }

    public boolean isExpired() {
        return expired;
    }

    private float computeAlpha() {
        double fadeFraction = FADE_START_FRACTION * LIFETIME_SECONDS;
        double timeLeft     = LIFETIME_SECONDS - elapsedSeconds;
        if (timeLeft >= fadeFraction) {
            return 1.0f;
        }
        return (float) Math.max(0.0, timeLeft / fadeFraction);
    }

    private static String formatDamage(double damage) {
        int rounded = (int) Math.round(damage);
        return String.valueOf(rounded);
    }
}
