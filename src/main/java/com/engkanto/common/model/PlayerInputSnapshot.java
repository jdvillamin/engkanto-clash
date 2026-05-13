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
