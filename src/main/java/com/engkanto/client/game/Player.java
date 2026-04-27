package com.engkanto.client.game;

import com.engkanto.client.input.KeyboardInput;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

public final class Player {
    public static final int SIZE = 32;

    private static final double SPEED_PIXELS_PER_SECOND = 180.0;

    private double x;
    private double y;

    public Player(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void update(KeyboardInput keyboardInput, double deltaSeconds) {
        double dx = 0.0;
        double dy = 0.0;

        if (keyboardInput.isUpPressed()) {
            dy -= 1.0;
        }
        if (keyboardInput.isDownPressed()) {
            dy += 1.0;
        }
        if (keyboardInput.isLeftPressed()) {
            dx -= 1.0;
        }
        if (keyboardInput.isRightPressed()) {
            dx += 1.0;
        }

        if (dx != 0.0 || dy != 0.0) {
            double length = Math.sqrt(dx * dx + dy * dy);
            x += (dx / length) * SPEED_PIXELS_PER_SECOND * deltaSeconds;
            y += (dy / length) * SPEED_PIXELS_PER_SECOND * deltaSeconds;
        }

        keepInsideScreen();
    }

    public void draw(Graphics2D graphics) {
        Rectangle2D.Double body = new Rectangle2D.Double(x, y, SIZE, SIZE);

        graphics.setColor(new Color(234, 194, 96));
        graphics.fill(body);

        graphics.setColor(new Color(77, 52, 28));
        graphics.draw(body);
    }

    private void keepInsideScreen() {
        x = clamp(x, 0.0, GameConfig.SCREEN_WIDTH - SIZE);
        y = clamp(y, 0.0, GameConfig.SCREEN_HEIGHT - SIZE);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
