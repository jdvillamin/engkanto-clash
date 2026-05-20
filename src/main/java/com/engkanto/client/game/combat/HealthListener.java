/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file defines callbacks for health changes. Other classes can listen for damage, healing, or death events.
 */
package com.engkanto.client.game.combat;

public interface HealthListener {
    void onDamage(double damage);
    
    void onHeal(double amount);

    void onDeath();
}