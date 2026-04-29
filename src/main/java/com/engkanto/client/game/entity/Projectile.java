package com.engkanto.client.game.entity;

import com.engkanto.client.game.GameConfig;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class Projectile {
    private static final double SPEED_PIXELS_PER_SECOND = 360.0;
    private static final int DRAW_SIZE = 48;

    private final BufferedImage image;
    private final int direction;
    private double x;
    private final double y;
    private boolean active = true;

    public Projectile(BufferedImage image, double x, double y, int direction) {
        this.image = image;
        this.x = x;
        this.y = y;
        this.direction = direction;
    }

    public void update(double deltaSeconds) {
        x += direction * SPEED_PIXELS_PER_SECOND * deltaSeconds;
        active = x + DRAW_SIZE >= 0.0 && x <= GameConfig.SCREEN_WIDTH;
    }

    public void draw(Graphics2D graphics) {
        if (direction < 0) {
            graphics.drawImage(image, (int) x + DRAW_SIZE, (int) y, -DRAW_SIZE, DRAW_SIZE, null);
        } else {
            graphics.drawImage(image, (int) x, (int) y, DRAW_SIZE, DRAW_SIZE, null);
        }
    }

    public boolean isActive() {
        return active;
    }
}
