package com.engkanto.client.game.character;

public final class SpriteAnimator {
    private PlayerAction action = PlayerAction.IDLE;
    private int frameIndex;
    private double frameTimer;
    private boolean locked;

    public void play(PlayerAction nextAction) {
        if (locked) {
            return;
        }
        if (action == nextAction) {
            return;
        }

        start(nextAction, false);
    }

    public void playOnce(PlayerAction nextAction) {
        start(nextAction, true);
    }

    public void update(double deltaSeconds, CharacterDefinition character) {
        if (action.getFrameCount() <= 1) {
            return;
        }

        frameTimer += deltaSeconds;
        while (frameTimer >= character.getFrameDuration(action, frameIndex)) {
            frameTimer -= character.getFrameDuration(action, frameIndex);
            advanceFrame();
        }
    }

    public void resetToIdle() {
        start(PlayerAction.IDLE, false);
    }

    public PlayerAction getAction() {
        return action;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public void setFrameIndex(int nextFrameIndex) {
        frameIndex = Math.max(0, Math.min(nextFrameIndex, action.getFrameCount() - 1));
        frameTimer = 0.0;
    }

    public boolean isLocked() {
        return locked;
    }

    private void advanceFrame() {
        if (frameIndex < action.getFrameCount() - 1) {
            frameIndex++;
            return;
        }

        if (action.loops()) {
            frameIndex = 0;
            return;
        }

        if (locked) {
            locked = false;
            start(PlayerAction.IDLE, false);
        }
    }

    private void start(PlayerAction nextAction, boolean shouldLock) {
        action = nextAction;
        frameIndex = 0;
        frameTimer = 0.0;
        locked = shouldLock;
    }
}
