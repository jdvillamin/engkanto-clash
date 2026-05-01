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
    private final int drawSize;
    private double x;
    private final double y;
    private boolean active = true;
    private boolean damageApplied;

    private final DamageComponent damage;

    public Projectile(BufferedImage image, double x, double y, int direction) {
        this(image, x, y, direction, DRAW_SIZE, 10.0);
    }

    public Projectile(BufferedImage image, double x, double y, int direction, int drawSize, double damageAmount) {
        this.image = image;
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.drawSize = Math.max(1, drawSize);
        this.damage = new DamageComponent(damageAmount);
    }

    public void update(double deltaSeconds) {
        x += direction * SPEED_PIXELS_PER_SECOND * deltaSeconds;
        active = x + drawSize >= 0.0 && x <= GameConfig.SCREEN_WIDTH;
    }

    public void draw(Graphics2D graphics) {
        if (direction < 0) {
            graphics.drawImage(image, (int) x + drawSize, (int) y, -drawSize, drawSize, null);
        } else {
            graphics.drawImage(image, (int) x, (int) y, drawSize, drawSize, null);
        }
    }

    public boolean isActive() {
        return active;
    }

    public double hit(HealthComponent target) {
        if (damageApplied) {
            return 0.0;
        }

        double dealt = damage.hit(target);
        if (dealt > 0.0) {
            damageApplied = true;
            active = false;
        }
        return dealt;
    }

    public double getX() { 
        return x; 
    }

    public double getY() {
        return y;
    }

    public int getDrawSize() {
        return drawSize;
    }
}
