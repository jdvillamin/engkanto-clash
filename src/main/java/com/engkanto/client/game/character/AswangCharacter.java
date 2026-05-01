package com.engkanto.client.game.character;

import com.engkanto.client.game.combat.DamageComponent;
import com.engkanto.client.game.combat.HealthComponent;
import com.engkanto.client.game.entity.Player;

public final class AswangCharacter extends SpriteCharacter {
    private static final double MOVE_1_DAMAGE = 15.0;
    private static final double MOVE_2_COOLDOWN_SECONDS = 1.0;
    private static final double MOVE_3_HEAL_AMOUNT = 25.0;
    private static final double SPECIAL_IMPACT_DAMAGE = 10.0;
    private static final double SPECIAL_POISON_DAMAGE = 5.0;
    private static final double SPECIAL_POISON_TICK_SECONDS = 0.5;
    private static final double SPECIAL_POISON_DURATION_SECONDS = 4.0;
    private static final int SPECIAL_HIT_FRAME_INDEX = 2;
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
    public void onMove3(Player player) {
        player.heal(MOVE_3_HEAL_AMOUNT);
    }

    @Override
    public boolean canDirectAttackHit(PlayerAction action, int frameIndex) {
        if (action == PlayerAction.MOVE_3) {
            return false;
        }
        if (action == PlayerAction.SPECIAL) {
            return frameIndex == SPECIAL_HIT_FRAME_INDEX;
        }
        return true;
    }

    @Override
    public boolean applyDirectAttack(PlayerAction action, HealthComponent target, DamageComponent damage) {
        if (action == PlayerAction.MOVE_1) {
            return damage.hit(target, MOVE_1_DAMAGE) > 0.0;
        }
        if (action == PlayerAction.SPECIAL) {
            double impactDamage = damage.hit(target, SPECIAL_IMPACT_DAMAGE);
            boolean poisoned = target.applyPoison(
                    SPECIAL_POISON_DAMAGE,
                    SPECIAL_POISON_TICK_SECONDS,
                    SPECIAL_POISON_DURATION_SECONDS
            );
            return impactDamage > 0.0 || poisoned;
        }
        return damage.hit(target) > 0.0;
    }

    @Override
    public boolean locksMovement(PlayerAction action) {
        return action == PlayerAction.MOVE_2
                || action == PlayerAction.MOVE_3
                || action == PlayerAction.SPECIAL;
    }

    @Override
    public double getCooldown(PlayerAction action, double defaultCooldownSeconds) {
        if (action == PlayerAction.MOVE_2) {
            return MOVE_2_COOLDOWN_SECONDS;
        }
        return defaultCooldownSeconds;
    }

    private boolean isGliding(Player player, boolean glideHeld) {
        return glideHeld
                && player.getCurrentAction() == PlayerAction.JUMP
                && player.getCurrentFrameIndex() == 2;
    }
}
