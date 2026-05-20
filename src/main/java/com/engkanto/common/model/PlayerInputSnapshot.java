/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file stores one set of player input for networking. The client sends it to the server so the server can update movement and attacks.
 */
package com.engkanto.common.model;

public final class PlayerInputSnapshot {
    public long sequence;
    public boolean upPressed;
    public boolean downPressed;
    public boolean leftPressed;
    public boolean rightPressed;
    public boolean glidePressed;
    public boolean move1Requested;
    public boolean move2Requested;
    public boolean move3Requested;
    public boolean specialRequested;
    public boolean switchCharacterRequested;

    public PlayerInputSnapshot() {
    }

    public PlayerInputSnapshot(long sequence) {
        this.sequence = sequence;
    }
}
