package com.engkanto.client.game.combat;

import java.util.ArrayList;
import java.util.List;

public class HealthComponent {
    private double currentHealth;
    private final double maxHealth;
    private boolean isDead = false;
    
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

    public void kill() {
        if (isDead) return;
        currentHealth = 0.0;
        isDead = true;
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
        for (HealthListener listener : new ArrayList<>(listeners)) {
            listener.onDeath();
        }
    }
}
