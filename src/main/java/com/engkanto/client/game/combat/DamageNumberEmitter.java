/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file manages floating damage numbers. It adds new numbers, updates them, and removes them when they finish fading.
 */
package com.engkanto.client.game.combat;

import java.awt.Graphics2D;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DamageNumberEmitter {
    private final List<DamageNumber> active = new CopyOnWriteArrayList<>();

    public void emit(double centerX, double topY, double damage) {
        active.add(new DamageNumber(centerX, topY, damage));
    }

    public void update(double deltaSeconds) {
        for (DamageNumber number : active) {
            number.update(deltaSeconds);
            if (number.isExpired()) {
                active.remove(number);
            }
        }
    }

    public void draw(Graphics2D graphics) {
        for (DamageNumber number : active) {
            number.draw(graphics);
        }
    }
}
