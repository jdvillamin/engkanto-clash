package com.engkanto.client.game.combat;

import java.util.ArrayList;
import java.util.List;

public class HealthComponent {
    private double currentHealth;
    private final double maxHealth;
    private boolean isDead = false;
    private final List<PoisonEffect> poisonEffects = new ArrayList<>();
    
    private final List<HealthListener> listeners = new ArrayList<>();

    public HealthComponent(double maxHealth) {
        this.maxHealth = sanitizeMaxHealth(maxHealth);
        this.currentHealth = this.maxHealth;
    }

    public void takeDamage(double damage) {
        if (isDead || !isPositiveFinite(damage)) return;
        
        double oldHealth = currentHealth;
        currentHealth = Math.max(0.0, currentHealth - damage);
        double actualDamage = oldHealth - currentHealth;
        
        if (actualDamage > 0.0) {
            notifyDamage(actualDamage);
        }
        if (currentHealth <= 0.0 && !isDead) {
            isDead = true;
            notifyDeath();
        }
    }
    
    public void heal(double amount) {
        if (isDead || !isPositiveFinite(amount)) return;
        
        double oldHealth = currentHealth;
        currentHealth = Math.min(maxHealth, currentHealth + amount);
        double actualHeal = currentHealth - oldHealth;
        if (actualHeal > 0.0) {
            notifyHeal(actualHeal);
        }
    }

    public void update(double deltaSeconds) {
        if (isDead || !isPositiveFinite(deltaSeconds)) {
            return;
        }

        for (int index = poisonEffects.size() - 1; index >= 0; index--) {
            PoisonEffect poison = poisonEffects.get(index);
            boolean expired = poison.update(this, deltaSeconds);
            if (isDead) {
                return;
            }
            if (expired) {
                poisonEffects.remove(index);
            }
        }
    }

    public boolean applyPoison(double damagePerTick, double tickIntervalSeconds, double durationSeconds) {
        if (isDead
                || !isPositiveFinite(damagePerTick)
                || !isPositiveFinite(tickIntervalSeconds)
                || !isPositiveFinite(durationSeconds)) {
            return false;
        }

        poisonEffects.add(new PoisonEffect(damagePerTick, tickIntervalSeconds, durationSeconds));
        return true;
    }

    public void kill() {
        if (isDead) return;
        currentHealth = 0.0;
        isDead = true;
        poisonEffects.clear();
        notifyDeath();
    }

    public void fullHeal() {
        if (isDead) return;
        double oldHealth = currentHealth;
        currentHealth = maxHealth;
        double actualHeal = currentHealth - oldHealth;
        if (actualHeal > 0.0) {
            notifyHeal(actualHeal);
        }
    }

    public void revive() {
        if (!isDead) return;
        isDead = false;
        currentHealth = maxHealth;
        poisonEffects.clear();
        notifyHeal(maxHealth);
    }
    
    public void addListener(HealthListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(HealthListener listener) {
        listeners.remove(listener);
    }
    
    public double getCurrentHealth() { return currentHealth; }
    public double getMaxHealth() { return maxHealth; }
    public double getHealthPercentage() { return currentHealth / maxHealth; }
    public boolean isDead() { return isDead; }
    public boolean isFullHealth() { return Math.abs(currentHealth - maxHealth) < 0.01; }

    private double sanitizeMaxHealth(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 1.0;
        }
        return value;
    }

    private boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
    
    private void notifyDamage(double damage) {
        for (HealthListener listener : new ArrayList<>(listeners)) {
            listener.onDamage(damage);
        }
    }
    
    private void notifyHeal(double amount) {
        for (HealthListener listener : new ArrayList<>(listeners)) {
            listener.onHeal(amount);
        }
    }
    
    private void notifyDeath() {
        poisonEffects.clear();
        for (HealthListener listener : new ArrayList<>(listeners)) {
            listener.onDeath();
        }
    }

    private static final class PoisonEffect {
        private final double damagePerTick;
        private final double tickIntervalSeconds;
        private double secondsRemaining;
        private double secondsUntilNextTick;

        private PoisonEffect(double damagePerTick, double tickIntervalSeconds, double durationSeconds) {
            this.damagePerTick = damagePerTick;
            this.tickIntervalSeconds = tickIntervalSeconds;
            this.secondsRemaining = durationSeconds;
            this.secondsUntilNextTick = tickIntervalSeconds;
        }

        private boolean update(HealthComponent health, double deltaSeconds) {
            double elapsedSeconds = Math.min(deltaSeconds, secondsRemaining);
            secondsRemaining -= elapsedSeconds;
            secondsUntilNextTick -= elapsedSeconds;

            while (secondsUntilNextTick <= 0.0 && !health.isDead()) {
                health.takeDamage(damagePerTick);
                secondsUntilNextTick += tickIntervalSeconds;
            }

            return secondsRemaining <= 0.0 || health.isDead();
        }
    }
}
