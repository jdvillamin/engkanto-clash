package com.engkanto.client.game.character;

import com.engkanto.client.game.entity.Player;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public interface CharacterDefinition {
    String getName();

    BufferedImage getFrame(PlayerAction action, int frameIndex);

    default double getFrameDuration(PlayerAction action, int frameIndex) {
        return action.getFrameDuration(frameIndex);
    }

    default void update(Player player, double deltaSeconds) {
    }

    default void drawEffects(Graphics2D graphics) {
    }

    default double getHorizontalVelocity() {
        return 0.0;
    }

    default double getGravityScale(Player player, boolean glideHeld) {
        return 1.0;
    }

    default double getMaximumFallVelocity(Player player, boolean glideHeld) {
        return Double.POSITIVE_INFINITY;
    }

    default int getFallingJumpFrame(Player player, boolean glideHeld) {
        return 2;
    }

    default void onMove1(Player player) {
        cancelMovementEffect();
    }

    default void onMove2(Player player) {
        cancelMovementEffect();
    }

    default void onMove3(Player player) {
        cancelMovementEffect();
    }

    default void onSpecial(Player player) {
        cancelMovementEffect();
    }

    default void cancelMovementEffect() {
    }

    default boolean locksMovement(PlayerAction action) {
        return false;
    }
}
