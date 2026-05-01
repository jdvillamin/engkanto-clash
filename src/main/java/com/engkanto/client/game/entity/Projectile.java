package com.engkanto.client.game.entity;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import com.engkanto.client.game.GameConfig;
import com.engkanto.client.game.combat.DamageComponent;
import com.engkanto.client.game.combat.HealthComponent;

public final class Projectile {
    private static final double SPEED_PIXELS_PER_SECOND = 360.0;
    public static final int DRAW_SIZE = 48;

    private final BufferedImage image;
    private final int direction;
    private double x;
    private final double y;
    private boolean active = true;

    private final DamageComponent damage = new DamageComponent(10.0);

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

    public double hit(HealthComponent target) {
        double dealt = damage.hit(target);
        if (dealt > 0.0) {
            active = false;
        }
        return dealt;
    }

    public double getX() { 
        return x; 
    }
}
