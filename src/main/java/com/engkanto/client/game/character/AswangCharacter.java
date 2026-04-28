package com.engkanto.client.game.character;

import com.engkanto.client.game.entity.Player;

public final class AswangCharacter extends SpriteCharacter {
    private static final double GLIDE_GRAVITY_SCALE = 0.22;
    private static final double GLIDE_MAX_FALL_VELOCITY_PIXELS_PER_SECOND = 150.0;

    public AswangCharacter() {
        super("Aswang", "/assets/sprites/aswang.png");
    }

    @Override
    public double getFrameDuration(PlayerAction action, int frameIndex) {
        if (action == PlayerAction.MOVE_3) {
            return frameIndex == 2 ? 0.42 : 0.10;
        }
        if (action == PlayerAction.SPECIAL) {
            return frameIndex == 1 ? 0.48 : 0.18;
        }
        return super.getFrameDuration(action, frameIndex);
    }

    @Override
    public double getGravityScale(Player player, boolean glideHeld) {
        if (isGliding(player, glideHeld)) {
            return GLIDE_GRAVITY_SCALE;
        }
        return 1.0;
    }

    @Override
    public double getMaximumFallVelocity(Player player, boolean glideHeld) {
        if (isGliding(player, glideHeld)) {
            return GLIDE_MAX_FALL_VELOCITY_PIXELS_PER_SECOND;
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public int getFallingJumpFrame(Player player, boolean glideHeld) {
        return glideHeld ? 2 : 1;
    }

    @Override
    public boolean locksMovement(PlayerAction action) {
        return action == PlayerAction.MOVE_3;
    }

    private boolean isGliding(Player player, boolean glideHeld) {
        return glideHeld
                && player.getCurrentAction() == PlayerAction.JUMP
                && player.getCurrentFrameIndex() == 2;
    }
}
