package com.engkanto.common.model;

public final class PlayerSnapshot {
    public int id;
    public double x;
    public double y;
    public boolean facingLeft;
    public int characterIndex;
    public String characterName;
    public String action;
    public int frameIndex;
    public double health;
    public double maxHealth;
    public boolean dead;
    public double move1CooldownRemaining;
    public double move1CooldownDuration;
    public double move2CooldownRemaining;
    public double move2CooldownDuration;
    public double move3CooldownRemaining;
    public double move3CooldownDuration;
    public double specialCooldownRemaining;
    public double specialCooldownDuration;
    public int kills;

    public PlayerSnapshot() {
    }
}
