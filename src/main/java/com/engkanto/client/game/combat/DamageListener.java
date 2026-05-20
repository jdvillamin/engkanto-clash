/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file defines a listener for damage events. Other classes can use it to react when damage is dealt.
 */
package com.engkanto.client.game.combat;

public interface DamageListener {
    void onHit(HealthComponent target, double actualDamage);

    void onKill(HealthComponent target);
}
