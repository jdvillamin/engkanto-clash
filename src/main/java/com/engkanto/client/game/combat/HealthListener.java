package com.engkanto.client.game.combat;

public interface HealthListener {
    void onDamage(double damage);
    
    void onHeal(double amount);

    void onDeath();
}