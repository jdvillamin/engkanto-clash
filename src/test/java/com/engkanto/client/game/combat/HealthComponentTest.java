package com.engkanto.client.game.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class HealthComponentTest {
    @Test
    void damageAndHealingAreClampedToActualHealthChanges() {
        HealthComponent health = new HealthComponent(100.0);
        RecordingHealthListener listener = new RecordingHealthListener();
        health.addListener(listener);

        health.takeDamage(150.0);

        assertEquals(0.0, health.getCurrentHealth());
        assertTrue(health.isDead());
        assertEquals(List.of(100.0), listener.damageEvents);
        assertEquals(1, listener.deathEvents);

        health.heal(50.0);

        assertEquals(0.0, health.getCurrentHealth());
        assertEquals(List.of(), listener.healEvents);
    }

    @Test
    void fullHealReportsActualAmountRestored() {
        HealthComponent health = new HealthComponent(100.0);
        RecordingHealthListener listener = new RecordingHealthListener();
        health.addListener(listener);

        health.takeDamage(35.0);
        health.fullHeal();
        health.fullHeal();

        assertEquals(100.0, health.getCurrentHealth());
        assertEquals(List.of(35.0), listener.damageEvents);
        assertEquals(List.of(35.0), listener.healEvents);
        assertFalse(health.isDead());
    }

    @Test
    void healReportsOnlyEffectiveHealAmount() {
        HealthComponent health = new HealthComponent(100.0);
        RecordingHealthListener listener = new RecordingHealthListener();
        health.addListener(listener);

        health.takeDamage(20.0);
        health.heal(50.0);

        assertEquals(100.0, health.getCurrentHealth());
        assertEquals(List.of(20.0), listener.damageEvents);
        assertEquals(List.of(20.0), listener.healEvents);
    }

    @Test
    void invalidValuesAreIgnoredOrSanitized() {
        HealthComponent health = new HealthComponent(Double.NaN);
        RecordingHealthListener listener = new RecordingHealthListener();
        health.addListener(listener);

        assertEquals(1.0, health.getMaxHealth());
        assertEquals(1.0, health.getCurrentHealth());

        health.takeDamage(Double.NaN);
        health.takeDamage(Double.POSITIVE_INFINITY);
        health.takeDamage(-1.0);
        health.heal(Double.NaN);
        health.heal(Double.POSITIVE_INFINITY);
        health.heal(-1.0);

        assertEquals(1.0, health.getCurrentHealth());
        assertFalse(health.isDead());
        assertTrue(listener.damageEvents.isEmpty());
        assertTrue(listener.healEvents.isEmpty());
        assertEquals(0, listener.deathEvents);
    }

    @Test
    void poisonDealsDamageOnEachTickUntilDurationEnds() {
        HealthComponent health = new HealthComponent(100.0);
        RecordingHealthListener listener = new RecordingHealthListener();
        health.addListener(listener);

        assertTrue(health.applyPoison(5.0, 0.5, 4.0));
        for (int tick = 0; tick < 8; tick++) {
            health.update(0.5);
        }
        health.update(0.5);

        assertEquals(60.0, health.getCurrentHealth());
        assertEquals(List.of(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0), listener.damageEvents);
        assertFalse(health.isDead());
    }

    private static final class RecordingHealthListener implements HealthListener {
        private final List<Double> damageEvents = new ArrayList<>();
        private final List<Double> healEvents = new ArrayList<>();
        private int deathEvents;

        @Override
        public void onDamage(double damage) {
            damageEvents.add(damage);
        }

        @Override
        public void onHeal(double amount) {
            healEvents.add(amount);
        }

        @Override
        public void onDeath() {
            deathEvents++;
        }
    }
}
