package com.engkanto.client.game.combat;

import java.util.ArrayList;
import java.util.List;

public class HealthComponent {
    private double currentHealth;
    private final double maxHealth;
    private boolean isDead = false;
    
    private final List<HealthListener> listeners = new ArrayList<>();

    public HealthComponent(double maxHealth) {
        this.maxHealth = Math.max(1.0, maxHealth);
        this.currentHealth = this.maxHealth;
    }

    public void takeDamage(double damage) {
        if (isDead || damage <= 0) return;
        
        double oldHealth = currentHealth;
        currentHealth = Math.max(0.0, currentHealth - damage);
        
        if (currentHealth <= 0.0) {
            isDead = true;
            notifyDeath();
        } else {
            notifyDamage(damage);
        }
    }
    
    public void heal(double amount) {
        if (isDead || amount <= 0) return;
        
        double oldHealth = currentHealth;
        currentHealth = Math.min(maxHealth, currentHealth + amount);
        notifyHeal(currentHealth - oldHealth);
    }

    public void kill() {
        if (isDead) return;
        currentHealth = 0.0;
        isDead = true;
        notifyDeath();
    }

    public void fullHeal() {
        if (isDead) return;
        currentHealth = maxHealth;
        notifyHeal(maxHealth - currentHealth);
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
    public double getHealthPercentage() { return maxHealth > 0 ? currentHealth / maxHealth : 0.0; }
    public boolean isDead() { return isDead; }
    public boolean isFullHealth() { return Math.abs(currentHealth - maxHealth) < 0.01; }
    
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