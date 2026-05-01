package com.engkanto.client.game.character;

import com.engkanto.client.game.combat.DamageComponent;
import com.engkanto.client.game.combat.HealthComponent;
import com.engkanto.client.game.entity.Player;

public final class TikbalangCharacter extends SpriteCharacter {
    private static final double MOVE_1_DAMAGE = 15.0;
    private static final double MOVE_2_COOLDOWN_SECONDS = 1.0;
    private static final double DASH_VELOCITY_PIXELS_PER_SECOND = 540.0;
    private static final double DASH_DECAY_PER_SECOND = 1_800.0;

    private int committedDashDirection;
    private boolean specialDashPending;
    private double dashVelocity;

    public TikbalangCharacter() {
        super("Tikbalang", "/assets/sprites/tikbalang.png");
    }

    @Override
    public double getFrameDuration(PlayerAction action, int frameIndex) {
        if (action == PlayerAction.SPECIAL) {
            return frameIndex == 0 ? 0.40 : 0.20;
        }
        return super.getFrameDuration(action, frameIndex);
    }

    @Override
    public void update(Player player, double deltaSeconds) {
        if (specialDashPending) {
            if (player.getCurrentAction() == PlayerAction.SPECIAL && player.getCurrentFrameIndex() > 0) {
                dashVelocity = committedDashDirection * DASH_VELOCITY_PIXELS_PER_SECOND;
                specialDashPending = false;
            } else {
                dashVelocity = 0.0;
                return;
            }
        }

        if (isDashActionLocked(player)) {
            dashVelocity = committedDashDirection * DASH_VELOCITY_PIXELS_PER_SECOND;
            return;
        }

        committedDashDirection = 0;
        specialDashPending = false;
        if (dashVelocity > 0.0) {
            dashVelocity = Math.max(0.0, dashVelocity - DASH_DECAY_PER_SECOND * deltaSeconds);
        } else if (dashVelocity < 0.0) {
            dashVelocity = Math.min(0.0, dashVelocity + DASH_DECAY_PER_SECOND * deltaSeconds);
        }
    }

    @Override
    public void onMove1(Player player) {
        cancelMovementEffect();
    }

    @Override
    public void onMove2(Player player) {
        cancelMovementEffect();
    }

    @Override
    public void onMove3(Player player) {
        startDash(player);
    }

    @Override
    public void onSpecial(Player player) {
        prepareSpecialDash(player);
    }

    @Override
    public void cancelMovementEffect() {
        dashVelocity = 0.0;
        committedDashDirection = 0;
        specialDashPending = false;
    }

    @Override
    public boolean locksMovement(PlayerAction action) {
        return action == PlayerAction.MOVE_2
                || action == PlayerAction.MOVE_3
                || action == PlayerAction.SPECIAL;
    }

    @Override
    public boolean canDirectAttackHit(PlayerAction action, int frameIndex) {
        return action != PlayerAction.MOVE_3;
    }

    @Override
    public boolean applyDirectAttack(PlayerAction action, HealthComponent target, DamageComponent damage) {
        if (action == PlayerAction.MOVE_1) {
            return damage.hit(target, MOVE_1_DAMAGE) > 0.0;
        }
        return damage.hit(target) > 0.0;
    }

    @Override
    public double getCooldown(PlayerAction action, double defaultCooldownSeconds) {
        if (action == PlayerAction.MOVE_2) {
            return MOVE_2_COOLDOWN_SECONDS;
        }
        return defaultCooldownSeconds;
    }

    @Override
    public double getHorizontalVelocity() {
        return dashVelocity;
    }

    private void startDash(Player player) {
        committedDashDirection = player.isFacingLeft() ? -1 : 1;
        dashVelocity = committedDashDirection * DASH_VELOCITY_PIXELS_PER_SECOND;
    }

    private void prepareSpecialDash(Player player) {
        committedDashDirection = player.isFacingLeft() ? -1 : 1;
        dashVelocity = 0.0;
        specialDashPending = true;
    }

    private boolean isDashActionLocked(Player player) {
        return committedDashDirection != 0
                && player.isActionLocked()
                && locksMovement(player.getCurrentAction());
    }
}
