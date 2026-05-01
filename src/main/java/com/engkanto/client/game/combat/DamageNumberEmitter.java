package com.engkanto.client.game.combat;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DamageNumberEmitter {
    private final List<DamageNumber> active = new ArrayList<>();

    public void emit(double centerX, double topY, double damage) {
        active.add(new DamageNumber(centerX, topY, damage));
    }

    public void update(double deltaSeconds) {
        Iterator<DamageNumber> it = active.iterator();
        while (it.hasNext()) {
            DamageNumber number = it.next();
            number.update(deltaSeconds);
            if (number.isExpired()) {
                it.remove();
            }
        }
    }

    public void draw(Graphics2D graphics) {
        for (DamageNumber number : active) {
            number.draw(graphics);
        }
    }
}
