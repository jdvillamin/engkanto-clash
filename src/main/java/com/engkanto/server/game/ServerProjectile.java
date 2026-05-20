/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file represents a projectile on the server. It moves projectiles and tracks their owner, damage, and hit box.
 */
package com.engkanto.server.game;

final class ServerProjectile {
    private static final double SPEED = 360.0;
    private static final int SCREEN_WIDTH = 1280;

    private final int ownerId;
    private final double damage;
    private final int direction;
    private final int size;
    private final double y;
    private double x;
    private boolean active = true;

    ServerProjectile(int ownerId, double x, double y, int direction, int size, double damage) {
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.size = size;
        this.damage = damage;
    }

    void update(double deltaSeconds) {
        x += direction * SPEED * deltaSeconds;
        if (x + size < 0 || x > SCREEN_WIDTH) {
            active = false;
        }
    }

    boolean overlaps(double targetX, double targetY) {
        return x + size > targetX
                && x < targetX + ServerPlayer.SIZE
                && y + size > targetY
                && y < targetY + ServerPlayer.SIZE;
    }

    boolean isActive() {
        return active;
    }

    int getOwnerId() {
        return ownerId;
    }

    double getDamage() {
        return damage;
    }
}
