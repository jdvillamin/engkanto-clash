package com.engkanto.client.game.entity;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import com.engkanto.client.game.GameConfig;
import com.engkanto.client.game.character.AswangCharacter;
import com.engkanto.client.game.character.CharacterDefinition;
import com.engkanto.client.game.character.EngkantoCharacter;
import com.engkanto.client.game.character.KapreCharacter;
import com.engkanto.client.game.character.PlayerAction;
import com.engkanto.client.game.character.SpriteAnimator;
import com.engkanto.client.game.character.TikbalangCharacter;
import com.engkanto.client.game.combat.DamageComponent;
import com.engkanto.client.game.combat.HealthComponent;
import com.engkanto.client.game.combat.HealthListener;
import com.engkanto.client.game.world.Platform;
import com.engkanto.client.input.KeyboardInput;

public final class Player {
    public static final int SIZE = 96;
    private static final double MAX_HEALTH = 100.0; 

    private static final double SPEED_PIXELS_PER_SECOND = 180.0;
    private static final double JUMP_VELOCITY_PIXELS_PER_SECOND = -560.0;
    private static final double GRAVITY_PIXELS_PER_SECOND = 1_200.0;
    private static final double MOVE_1_COOLDOWN_SECONDS = 0.35;
    private static final double MOVE_2_COOLDOWN_SECONDS = 0.65;
    private static final double MOVE_3_COOLDOWN_SECONDS = 1.10;
    private static final double SPECIAL_COOLDOWN_SECONDS = 2.00;
    private static final double JUMP_TAKEOFF_FRAME_SECONDS = 0.10;
    private static final double LANDING_FRAME_SECONDS = 0.16;

    private final CharacterDefinition[] characters;
    private final SpriteAnimator animator;
    private final HealthComponent health;
    private final DamageComponent damage;
    private int activeCharacterIndex;

    private double x;
    private double y;
    private double groundY;
    private double verticalVelocity;
    private boolean facingLeft;
    private double move1CooldownRemaining;
    private double move2CooldownRemaining;
    private double move3CooldownRemaining;
    private double specialCooldownRemaining;
    private double jumpElapsedSeconds;
    private double landingFrameRemaining;

    private double respawnTimerRemaining;
    private static final double RESPAWN_SECONDS = 1.0;

    public Player(double x, double y) {
        this.x = x;
        this.y = y;
        this.groundY = y;
        this.characters = new CharacterDefinition[] {
                new TikbalangCharacter(),
                new KapreCharacter(),
                new AswangCharacter(),
                new EngkantoCharacter()
        };
        this.animator = new SpriteAnimator();
        this.health = new HealthComponent(MAX_HEALTH); 
        this.damage = new DamageComponent(25.0);
        
        health.addListener(new HealthListener() {
            @Override
            public void onDamage(double damage) {

            }
            
            @Override
            public void onHeal(double amount) {
            }
            
            @Override
            public void onDeath() {
                animator.playOnce(PlayerAction.DEATH);
                respawnTimerRemaining = RESPAWN_SECONDS;
            }
        });
    }

    public void takeDamage(double damage) {
        health.takeDamage(damage);
    }
    
    public void heal(double amount) {
        health.heal(amount);
    }
    
    public double getHealth() {
        return health.getCurrentHealth();
    }
    
    public double getMaxHealth() {
        return health.getMaxHealth();
    }
    
    public double getHealthPercentage() {
        return health.getHealthPercentage();
    }
    
    public boolean isDead() {
        return health.isDead();
    }

    public void update(KeyboardInput keyboardInput, List<Platform> platforms, double deltaSeconds) {
        if (isDead()) {
            animator.update(deltaSeconds, getActiveCharacter());
            respawnTimerRemaining -= deltaSeconds;
            if (respawnTimerRemaining <= 0.0) {
                health.revive();
                animator.resetToIdle();
            }
            return;
        }

        updateCooldowns(deltaSeconds);
        switchCharacterIfRequested(keyboardInput);

        double dx = 0.0;
        double movementVelocity = 0.0;
        double previousBottom = getBottom();

        if (!isMovementLocked() && keyboardInput.isLeftPressed()) {
            dx -= 1.0;
            facingLeft = true;
        }
        if (!isMovementLocked() && keyboardInput.isRightPressed()) {
            dx += 1.0;
            facingLeft = false;
        }

        if (dx != 0.0) {
            movementVelocity = dx * SPEED_PIXELS_PER_SECOND;
        }

        PlayerAction requestedAction = consumeRequestedAction(keyboardInput);
        if (requestedAction != null) {
            animator.playOnce(requestedAction);
            if (isMovementLocked()) {
                movementVelocity = 0.0;
            }
        } else {
            animator.play(resolveAction(keyboardInput, dx));
        }

        if (!isMovementLocked() && keyboardInput.isUpPressed() && isOnGround()) {
            startJump();
        }

        updateJump(keyboardInput.isGlidePressed(), deltaSeconds);
        getActiveCharacter().update(this, deltaSeconds);
        x += (movementVelocity + getActiveCharacter().getHorizontalVelocity()) * deltaSeconds;
        landOnPlatformIfFalling(platforms, previousBottom);

        if (animator.getAction() == PlayerAction.JUMP && !animator.isLocked()) {
            updateJumpFrame(keyboardInput.isGlidePressed(), deltaSeconds);
        } else {
            animator.update(deltaSeconds, getActiveCharacter());
        }
        keepInsideScreen();
    }

    public void draw(Graphics2D graphics) {
        BufferedImage frame = getActiveCharacter().getFrame(
                animator.getAction(),
                animator.getFrameIndex()
        );

        if (facingLeft) {
            graphics.drawImage(frame, (int) x + SIZE, (int) y, -SIZE, SIZE, null);
        } else {
            graphics.drawImage(frame, (int) x, (int) y, SIZE, SIZE, null);
        }

        getActiveCharacter().drawEffects(graphics);
    }

    public HealthComponent getHealthComponent() {
        return health;
    }

    public DamageComponent getActiveDamageComponent() {
        return damage;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean isFacingLeft() {
        return facingLeft;
    }

    public PlayerAction getCurrentAction() {
        return animator.getAction();
    }

    public int getCurrentFrameIndex() {
        return animator.getFrameIndex();
    }

    public boolean isActionLocked() {
        return animator.isLocked();
    }

    public String getCharacterName() {
        return getActiveCharacter().getName();
    }

    public List<Projectile> getActiveCharacterProjectiles() {
        return getActiveCharacter().getProjectiles();
    }

    private void switchCharacterIfRequested(KeyboardInput keyboardInput) {
        if (!keyboardInput.consumeSwitchCharacterRequested() || animator.isLocked()) {
            return;
        }

        getActiveCharacter().cancelMovementEffect();
        activeCharacterIndex = (activeCharacterIndex + 1) % characters.length;
        getActiveCharacter().cancelMovementEffect();
        animator.resetToIdle();
        clearCooldowns();
    }

    private CharacterDefinition getActiveCharacter() {
        return characters[activeCharacterIndex];
    }

    private void keepInsideScreen() {
        x = clamp(x, 0.0, GameConfig.SCREEN_WIDTH - SIZE);
        groundY = clamp(groundY, 0.0, GameConfig.SCREEN_HEIGHT - SIZE);
        y = Math.min(y, groundY);
    }

    private PlayerAction resolveAction(KeyboardInput keyboardInput, double dx) {
        if (landingFrameRemaining > 0.0 || !isOnGround() || keyboardInput.isUpPressed()) {
            return PlayerAction.JUMP;
        }
        if (dx != 0.0) {
            return PlayerAction.WALK;
        }
        return PlayerAction.IDLE;
    }

    private PlayerAction consumeRequestedAction(KeyboardInput keyboardInput) {
        if (animator.isLocked()) {
            return null;
        }
        if (keyboardInput.consumeDeathRequested()) {
            getActiveCharacter().cancelMovementEffect();
            return PlayerAction.DEATH;
        }
        if (keyboardInput.consumeSpecialRequested()) {
            if (specialCooldownRemaining > 0.0) {
                return null;
            }
            specialCooldownRemaining = SPECIAL_COOLDOWN_SECONDS;
            getActiveCharacter().onSpecial(this);
            return PlayerAction.SPECIAL;
        }
        if (keyboardInput.consumeMove3Requested()) {
            if (move3CooldownRemaining > 0.0) {
                return null;
            }
            move3CooldownRemaining = MOVE_3_COOLDOWN_SECONDS;
            getActiveCharacter().onMove3(this);
            return PlayerAction.MOVE_3;
        }
        if (keyboardInput.consumeMove2Requested()) {
            if (move2CooldownRemaining > 0.0) {
                return null;
            }
            move2CooldownRemaining = MOVE_2_COOLDOWN_SECONDS;
            getActiveCharacter().onMove2(this);
            return PlayerAction.MOVE_2;
        }
        if (keyboardInput.consumeMove1Requested()) {
            if (move1CooldownRemaining > 0.0) {
                return null;
            }
            move1CooldownRemaining = MOVE_1_COOLDOWN_SECONDS;
            getActiveCharacter().onMove1(this);
            return PlayerAction.MOVE_1;
        }
        return null;
    }

    private void updateJump(boolean glideHeld, double deltaSeconds) {
        if (landingFrameRemaining > 0.0) {
            landingFrameRemaining = Math.max(0.0, landingFrameRemaining - deltaSeconds);
        }
        if (isOnGround() && verticalVelocity >= 0.0) {
            return;
        }

        jumpElapsedSeconds += deltaSeconds;
        y += verticalVelocity * deltaSeconds;
        verticalVelocity += GRAVITY_PIXELS_PER_SECOND
                * getActiveCharacter().getGravityScale(this, glideHeld)
                * deltaSeconds;
        verticalVelocity = Math.min(
                verticalVelocity,
                getActiveCharacter().getMaximumFallVelocity(this, glideHeld)
        );

        if (y >= groundY) {
            y = groundY;
            verticalVelocity = 0.0;
            landingFrameRemaining = LANDING_FRAME_SECONDS;
        }
    }

    private void startJump() {
        verticalVelocity = JUMP_VELOCITY_PIXELS_PER_SECOND;
        jumpElapsedSeconds = 0.0;
        landingFrameRemaining = 0.0;
    }

    private void updateJumpFrame(boolean glideHeld, double deltaSeconds) {
        if (landingFrameRemaining > 0.0) {
            animator.setFrameIndex(3);
            return;
        }

        if (!isOnGround()) {
            if (jumpElapsedSeconds < JUMP_TAKEOFF_FRAME_SECONDS) {
                animator.setFrameIndex(0);
            } else if (verticalVelocity < 0.0) {
                animator.setFrameIndex(1);
            } else {
                animator.setFrameIndex(getActiveCharacter().getFallingJumpFrame(this, glideHeld));
            }
            return;
        }

        animator.update(deltaSeconds, getActiveCharacter());
    }

    private boolean isOnGround() {
        return y >= groundY && verticalVelocity == 0.0;
    }

    private void landOnPlatformIfFalling(List<Platform> platforms, double previousBottom) {
        Platform standingPlatform = findStandingPlatform(platforms);
        if (standingPlatform != null && verticalVelocity == 0.0) {
            groundY = standingPlatform.getTop() - SIZE;
            y = groundY;
            return;
        }

        if (verticalVelocity < 0.0) {
            return;
        }

        for (Platform platform : platforms) {
            if (isLandingOn(platform, previousBottom)) {
                y = platform.getTop() - SIZE;
                groundY = y;
                verticalVelocity = 0.0;
                landingFrameRemaining = LANDING_FRAME_SECONDS;
                return;
            }
        }

        groundY = GameConfig.SCREEN_HEIGHT - SIZE;
    }

    private boolean isLandingOn(Platform platform, double previousBottom) {
        double currentBottom = getBottom();
        boolean crossesPlatformTop = previousBottom <= platform.getTop() && currentBottom >= platform.getTop();
        boolean overlapsHorizontally = getRight() > platform.getLeft() && getLeft() < platform.getRight();
        return crossesPlatformTop && overlapsHorizontally;
    }

    private Platform findStandingPlatform(List<Platform> platforms) {
        for (Platform platform : platforms) {
            if (isStandingOn(platform)) {
                return platform;
            }
        }
        return null;
    }

    private boolean isStandingOn(Platform platform) {
        boolean feetOnPlatformTop = Math.abs(getBottom() - platform.getTop()) < 1.0;
        boolean overlapsHorizontally = getRight() > platform.getLeft() && getLeft() < platform.getRight();
        return feetOnPlatformTop && overlapsHorizontally;
    }

    private boolean isMovementLocked() {
        return animator.isLocked() && getActiveCharacter().locksMovement(animator.getAction());
    }

    private double getLeft() {
        return x;
    }

    private double getRight() {
        return x + SIZE;
    }

    private double getBottom() {
        return y + SIZE;
    }

    private void updateCooldowns(double deltaSeconds) {
        move1CooldownRemaining = tickCooldown(move1CooldownRemaining, deltaSeconds);
        move2CooldownRemaining = tickCooldown(move2CooldownRemaining, deltaSeconds);
        move3CooldownRemaining = tickCooldown(move3CooldownRemaining, deltaSeconds);
        specialCooldownRemaining = tickCooldown(specialCooldownRemaining, deltaSeconds);
    }

    private double tickCooldown(double cooldownRemaining, double deltaSeconds) {
        return Math.max(0.0, cooldownRemaining - deltaSeconds);
    }

    private void clearCooldowns() {
        move1CooldownRemaining = 0.0;
        move2CooldownRemaining = 0.0;
        move3CooldownRemaining = 0.0;
        specialCooldownRemaining = 0.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}