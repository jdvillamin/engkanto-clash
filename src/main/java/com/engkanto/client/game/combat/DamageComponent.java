package com.engkanto.client.game.combat;

import java.util.ArrayList;
import java.util.List;

public class DamageComponent {
    private final double baseDamage;
    private boolean enabled;

    private final List<DamageListener> listeners = new ArrayList<>();

    public DamageComponent(double baseDamage) {
        this.baseDamage = sanitizeDamage(baseDamage);
        this.enabled = true;
    }

    public double hit(HealthComponent target) {
        return hit(target, baseDamage);
    }

    public double hit(HealthComponent target, double amount) {
        if (!enabled || target == null || target.isDead()) {
            return 0.0;
        }
        if (!isPositiveFinite(amount)) {
            return 0.0;
        }

        double healthBefore = target.getCurrentHealth();
        target.takeDamage(amount);
        double actualDamage = healthBefore - target.getCurrentHealth();

        if (actualDamage > 0.0) {
            notifyHit(target, actualDamage);
            if (target.isDead()) {
                notifyKill(target);
            }
        }

        return actualDamage;
    }

    public void addListener(DamageListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(DamageListener listener) {
        listeners.remove(listener);
    }

    public double getBaseDamage()           { return baseDamage; }
    public boolean isEnabled()              { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    private void notifyHit(HealthComponent target, double actualDamage) {
        for (DamageListener listener : new ArrayList<>(listeners)) {
            listener.onHit(target, actualDamage);
        }
    }

    private void notifyKill(HealthComponent target) {
        for (DamageListener listener : new ArrayList<>(listeners)) {
            listener.onKill(target);
        }
    }

    private double sanitizeDamage(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 1.0;
        }
        return value;
    }

    private boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
