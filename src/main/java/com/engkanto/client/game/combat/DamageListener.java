package com.engkanto.client.game.combat;

public interface DamageListener {
    void onHit(HealthComponent target, double actualDamage);

    void onKill(HealthComponent target);
}
