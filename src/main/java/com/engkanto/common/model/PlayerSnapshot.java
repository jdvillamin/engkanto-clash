/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file stores one player state in a server game snapshot. It includes position, action, health, cooldowns, kills, and effects.
 */
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
    public boolean invulnerable;
    public double rootedSecondsRemaining;
    public double hitFlashSecondsRemaining;

    public PlayerSnapshot() {
    }
}
