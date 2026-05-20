/*
 * Key Objects / Libraries Used
 *
 * PlayerInputSnapshot
 * - DTO containing the client's keyboard state for one tick.
 * - Received from the client via GameServer and applied with setInput().
 * - Reference: see com.engkanto.common.model.PlayerInputSnapshot
 *
 * PlayerSnapshot
 * - DTO sent to all clients each tick so they can render this player.
 * - Built by toSnapshot() from the player's current state.
 * - Reference: see com.engkanto.common.model.PlayerSnapshot
 *
 * Iterator
 * - Allows safe removal of elements while iterating a collection.
 * - Used in tickPoisons() to remove expired poison effects mid-loop.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html
 *
 * ArrayList / List
 * - Standard resizable list implementation.
 * - Used to store active PoisonEffect instances on this player.
 * - Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html
 */

package com.engkanto.server.game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.engkanto.common.model.PlayerInputSnapshot;
import com.engkanto.common.model.PlayerSnapshot;

final class ServerPlayer {
    static final int SIZE = 96;

    private static final int SCREEN_WIDTH = 1280;
    private static final int SCREEN_HEIGHT = 704;
    private static final double MAX_HEALTH = 100.0;
    private static final double SPEED_PIXELS_PER_SECOND = 180.0;
    private static final double JUMP_VELOCITY_PIXELS_PER_SECOND = -560.0;
    private static final double GRAVITY_PIXELS_PER_SECOND = 1_200.0;
    private static final double RESPAWN_SECONDS = 1.0;
    private static final double ATTACK_IMPACT_SECONDS = 0.18;
    private static final double INVULNERABILITY_SECONDS = 3.0;
    private static final double JUMP_TAKEOFF_FRAME_SECONDS = 0.10;
    private static final double LANDING_FRAME_SECONDS = 0.16;
    private static final double DASH_VELOCITY = 540.0;
    private static final double DASH_DECAY = 1_800.0;
    private static final double GLIDE_GRAVITY_SCALE = 0.22;
    private static final double GLIDE_MAX_FALL_VELOCITY = 150.0;
    private static final double VINE_ROOT_DURATION = 1.5;
    private static final int VINE_ROOT_SIZE = 96;
    private static final double HIT_FLASH_SECONDS = 0.15;
    private static final double POISON_DAMAGE_PER_TICK = 4.0;
    private static final double POISON_TICK_INTERVAL = 0.5;
    private static final double POISON_DURATION = 5.0;

    static final String[] CHARACTER_NAMES = {
            "Tikbalang", "Kapre", "Aswang", "Engkanto"
    };

    private final int id;
    private PlayerInputSnapshot input = new PlayerInputSnapshot();
    private double x;
    private double y;
    private double groundY;
    private double verticalVelocity;
    private double health = MAX_HEALTH;
    private double respawnTimerRemaining;
    private boolean facingLeft;
    private int characterIndex;
    private String action = "IDLE";
    private int frameIndex;
    private double frameTimer;
    private double actionElapsedSeconds;
    private boolean actionLocked;
    private boolean attackPending;
    private double pendingDamage;
    private boolean pendingRangedAttack;
    private double move1CooldownRemaining;
    private double move2CooldownRemaining;
    private double move3CooldownRemaining;
    private double specialCooldownRemaining;
    private double jumpElapsedSeconds;
    private double landingFrameRemaining;
    private double dashVelocity;
    private int committedDashDirection;
    private boolean specialDashPending;
    private double invulnerabilityRemaining;
    private double rootedSecondsRemaining;
    private boolean vineRootPending;
    private final List<PoisonEffect> poisonEffects = new ArrayList<>();
    private boolean pendingPoison;
    private double hitFlashSecondsRemaining;
    private int kills;

    ServerPlayer(int id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.groundY = y;
        this.facingLeft = id % 2 == 0;
        this.characterIndex = (id - 1) % CHARACTER_NAMES.length;
    }

    void setCharacterIndex(int characterIndex) {
        if (characterIndex >= 0 && characterIndex < CHARACTER_NAMES.length) {
            this.characterIndex = characterIndex;
        }
    }

    void setInput(PlayerInputSnapshot input) {
        if (input != null) {
            this.input = input;
        }
    }

    /*
     * update(double deltaSeconds, List<ServerPlatform> platforms)
     *
     * - Main per-tick update: handles input, movement, abilities, physics, and animation.
     */
    void update(double deltaSeconds, List<ServerPlatform> platforms) {
        if (isDead()) {
            updateDeath(deltaSeconds);
            return;
        }

        tickInvulnerability(deltaSeconds);
        tickHitFlash(deltaSeconds);
        tickRoot(deltaSeconds);
        tickCooldowns(deltaSeconds);
        switchCharacterIfRequested();

        double previousBottom = getBottom();
        double dx = 0.0;
        double movementVelocity = 0.0;

        if (!isMovementLocked() && input.leftPressed) {
            dx -= 1.0;
            facingLeft = true;
        }
        if (!isMovementLocked() && input.rightPressed) {
            dx += 1.0;
            facingLeft = false;
        }
        if (dx != 0.0) {
            movementVelocity = dx * SPEED_PIXELS_PER_SECOND;
        }

        String requestedAction = consumeRequestedAction();
        if (requestedAction != null) {
            startLockedAction(requestedAction);
            if (isMovementLocked()) {
                movementVelocity = 0.0;
            }
        } else if (!actionLocked) {
            play(resolveAction(dx));
        }

        if (!isMovementLocked() && input.upPressed && isOnGround()) {
            verticalVelocity = JUMP_VELOCITY_PIXELS_PER_SECOND;
            jumpElapsedSeconds = 0.0;
            landingFrameRemaining = 0.0;
        }

        dropThroughPlatformIfRequested(platforms);
        updateJump(deltaSeconds);
        updateDash(deltaSeconds);
        x += (movementVelocity + dashVelocity) * deltaSeconds;
        landOnPlatformIfFalling(platforms, previousBottom, input.downPressed);
        updateAnimation(deltaSeconds);
        updateJumpFrame();
        keepInsideScreen();
    }

    /*
     * hasAttackReady()
     *
     * - Returns true if an attack is pending and enough time has passed for impact.
     */
    boolean hasAttackReady() {
        return attackPending && actionElapsedSeconds >= ATTACK_IMPACT_SECONDS;
    }

    /*
     * canHit(ServerPlayer target)
     *
     * - Checks if this player's pending attack overlaps the target.
     */
    boolean canHit(ServerPlayer target) {
        if (target == this || target.isDead() || target.isInvulnerable()) {
            return false;
        }
        if (pendingRangedAttack) {
            return overlapsRangedAttack(target);
        }
        return overlapsMeleeAttack(target);
    }

    boolean isPendingRangedAttack() {
        return pendingRangedAttack;
    }

    void markAttackResolved() {
        attackPending = false;
        pendingPoison = false;
    }

    /*
     * takeDamage(double damage)
     *
     * - Reduces health by damage. Returns true if the player died.
     */
    boolean takeDamage(double damage) {
        if (damage <= 0.0 || isDead() || isInvulnerable()) {
            return false;
        }
        health = Math.max(0.0, health - damage);
        hitFlashSecondsRemaining = HIT_FLASH_SECONDS;
        if (health == 0.0) {
            action = "DEATH";
            frameIndex = 0;
            frameTimer = 0.0;
            actionLocked = true;
            respawnTimerRemaining = RESPAWN_SECONDS;
            return true;
        }
        return false;
    }

    void addKill() {
        kills++;
    }

    double getPendingDamage() {
        return pendingDamage;
    }

    /*
     * toSnapshot()
     *
     * - Builds a PlayerSnapshot from the current state to send to all clients.
     */
    PlayerSnapshot toSnapshot() {
        PlayerSnapshot snapshot = new PlayerSnapshot();
        snapshot.id = id;
        snapshot.x = x;
        snapshot.y = y;
        snapshot.facingLeft = facingLeft;
        snapshot.characterIndex = characterIndex;
        snapshot.characterName = CHARACTER_NAMES[characterIndex];
        snapshot.action = action;
        snapshot.frameIndex = frameIndex;
        snapshot.health = health;
        snapshot.maxHealth = MAX_HEALTH;
        snapshot.dead = isDead();
        snapshot.move1CooldownRemaining = move1CooldownRemaining;
        snapshot.move1CooldownDuration = getCooldown("MOVE_1");
        snapshot.move2CooldownRemaining = move2CooldownRemaining;
        snapshot.move2CooldownDuration = getCooldown("MOVE_2");
        snapshot.move3CooldownRemaining = move3CooldownRemaining;
        snapshot.move3CooldownDuration = getCooldown("MOVE_3");
        snapshot.specialCooldownRemaining = specialCooldownRemaining;
        snapshot.specialCooldownDuration = getCooldown("SPECIAL");
        snapshot.kills = kills;
        snapshot.invulnerable = isInvulnerable();
        snapshot.rootedSecondsRemaining = rootedSecondsRemaining;
        snapshot.hitFlashSecondsRemaining = hitFlashSecondsRemaining;
        return snapshot;
    }

    /*
     * updateDeath(double deltaSeconds)
     *
     * - Ticks the death animation and respawn timer, clears active effects.
     */
    private void updateDeath(double deltaSeconds) {
        updateAnimation(deltaSeconds);
        dashVelocity = 0.0;
        committedDashDirection = 0;
        specialDashPending = false;
        vineRootPending = false;
        rootedSecondsRemaining = 0.0;
        hitFlashSecondsRemaining = 0.0;
        poisonEffects.clear();
        respawnTimerRemaining -= deltaSeconds;
    }

    boolean isReadyToRespawn() {
        return isDead() && respawnTimerRemaining <= 0.0;
    }

    /*
     * respawnAt(double newX, double newY, double newGroundY)
     *
     * - Resets health, position, and state, then grants invulnerability.
     */
    void respawnAt(double newX, double newY, double newGroundY) {
        x = newX;
        y = newY;
        groundY = newGroundY;
        verticalVelocity = 0.0;
        health = MAX_HEALTH;
        actionLocked = false;
        rootedSecondsRemaining = 0.0;
        invulnerabilityRemaining = INVULNERABILITY_SECONDS;
        play("IDLE");
    }

    boolean isInvulnerable() {
        return invulnerabilityRemaining > 0.0;
    }

    private void tickInvulnerability(double deltaSeconds) {
        if (invulnerabilityRemaining > 0.0) {
            invulnerabilityRemaining = Math.max(0.0, invulnerabilityRemaining - deltaSeconds);
        }
    }

    private void tickHitFlash(double deltaSeconds) {
        if (hitFlashSecondsRemaining > 0.0) {
            hitFlashSecondsRemaining = Math.max(0.0, hitFlashSecondsRemaining - deltaSeconds);
        }
    }

    /*
     * switchCharacterIfRequested()
     *
     * - Cycles to the next character and resets cooldowns/dash state.
     */
    private void switchCharacterIfRequested() {
        if (!input.switchCharacterRequested || actionLocked) {
            return;
        }
        input.switchCharacterRequested = false;
        characterIndex = (characterIndex + 1) % CHARACTER_NAMES.length;
        move1CooldownRemaining = 0.0;
        move2CooldownRemaining = 0.0;
        move3CooldownRemaining = 0.0;
        specialCooldownRemaining = 0.0;
        dashVelocity = 0.0;
        committedDashDirection = 0;
        specialDashPending = false;
    }

    /*
     * consumeRequestedAction()
     *
     * - Returns the highest-priority ability that the player requested and is off cooldown.
     */
    private String consumeRequestedAction() {
        if (actionLocked) {
            return null;
        }
        if (input.specialRequested && specialCooldownRemaining <= 0.0) {
            input.specialRequested = false;
            specialCooldownRemaining = getCooldown("SPECIAL");
            return "SPECIAL";
        }
        if (input.move3Requested && move3CooldownRemaining <= 0.0) {
            input.move3Requested = false;
            move3CooldownRemaining = getCooldown("MOVE_3");
            return "MOVE_3";
        }
        if (input.move2Requested && move2CooldownRemaining <= 0.0) {
            input.move2Requested = false;
            move2CooldownRemaining = getCooldown("MOVE_2");
            return "MOVE_2";
        }
        if (input.move1Requested && move1CooldownRemaining <= 0.0) {
            input.move1Requested = false;
            move1CooldownRemaining = getCooldown("MOVE_1");
            return "MOVE_1";
        }
        return null;
    }

    /*
     * startLockedAction(String nextAction)
     *
     * - Begins a locked ability animation and sets up its side effects (damage, dash, heal, vine).
     */
    private void startLockedAction(String nextAction) {
        playOnce(nextAction);
        pendingDamage = getDamageFor(nextAction);
        pendingRangedAttack = isRangedAttack(nextAction);
        pendingPoison = characterIndex == 2 && "SPECIAL".equals(nextAction);
        attackPending = pendingDamage > 0.0;
        if (characterIndex == 2 && "MOVE_3".equals(nextAction)) {
            health = Math.min(MAX_HEALTH, health + 20.0);
        }
        if (characterIndex == 3 && "MOVE_3".equals(nextAction)) {
            vineRootPending = true;
        }
        if (characterIndex == 0) {
            if ("MOVE_3".equals(nextAction)) {
                committedDashDirection = facingLeft ? -1 : 1;
                dashVelocity = committedDashDirection * DASH_VELOCITY;
            } else if ("SPECIAL".equals(nextAction)) {
                committedDashDirection = facingLeft ? -1 : 1;
                dashVelocity = 0.0;
                specialDashPending = true;
            }
        }
    }

    private boolean isRangedAttack(String nextAction) {
        return characterIndex == 1 && "MOVE_3".equals(nextAction)
                || characterIndex == 3 && ("MOVE_1".equals(nextAction)
                || "MOVE_2".equals(nextAction)
                || "SPECIAL".equals(nextAction));
    }

    /*
     * getDamageFor(String nextAction)
     *
     * - Returns the damage value for a given move based on the current character.
     */
    private double getDamageFor(String nextAction) {
        return switch (characterIndex) {
            case 0 -> switch (nextAction) {
                case "MOVE_1" -> 12.0;
                case "MOVE_2" -> 22.0;
                case "SPECIAL" -> 35.0;
                default -> 0.0;
            };
            case 1 -> switch (nextAction) {
                case "MOVE_1" -> 12.0;
                case "MOVE_2" -> 22.0;
                case "MOVE_3" -> 8.0;
                case "SPECIAL" -> 30.0;
                default -> 0.0;
            };
            case 2 -> switch (nextAction) {
                case "MOVE_1" -> 12.0;
                case "MOVE_2" -> 18.0;
                case "SPECIAL" -> 8.0;
                default -> 0.0;
            };
            case 3 -> switch (nextAction) {
                case "MOVE_1" -> 6.0;
                case "MOVE_2" -> 12.0;
                case "SPECIAL" -> 18.0;
                default -> 0.0;
            };
            default -> 0.0;
        };
    }

    /*
     * getCooldown(String nextAction)
     *
     * - Returns the cooldown duration for a given move based on the current character.
     */
    private double getCooldown(String nextAction) {
        return switch (nextAction) {
            case "MOVE_1" -> characterIndex == 3 ? 0.25 : 0.30;
            case "MOVE_2" -> 1.50;
            case "MOVE_3" -> characterIndex == 2 ? 4.0 : 1.20;
            case "SPECIAL" -> 12.0;
            default -> 0.0;
        };
    }

    private String resolveAction(double dx) {
        if (!isOnGround() || input.upPressed) {
            return "JUMP";
        }
        if (dx != 0.0) {
            return "WALK";
        }
        return "IDLE";
    }

    /*
     * updateJump(double deltaSeconds)
     *
     * - Applies gravity and vertical movement; handles Aswang glide.
     */
    private void updateJump(double deltaSeconds) {
        if (landingFrameRemaining > 0.0) {
            landingFrameRemaining = Math.max(0.0, landingFrameRemaining - deltaSeconds);
        }
        if (isOnGround() && verticalVelocity >= 0.0) {
            return;
        }
        jumpElapsedSeconds += deltaSeconds;
        y += verticalVelocity * deltaSeconds;
        boolean gliding = isAswangGliding();
        double gravityScale = gliding ? GLIDE_GRAVITY_SCALE : 1.0;
        verticalVelocity += GRAVITY_PIXELS_PER_SECOND * gravityScale * deltaSeconds;
        if (gliding) {
            verticalVelocity = Math.min(verticalVelocity, GLIDE_MAX_FALL_VELOCITY);
        }
        if (y >= groundY) {
            y = groundY;
            verticalVelocity = 0.0;
            landingFrameRemaining = LANDING_FRAME_SECONDS;
        }
    }

    private boolean isAswangGliding() {
        return characterIndex == 2 && input.glidePressed && verticalVelocity > 0.0;
    }

    /*
     * dropThroughPlatformIfRequested(List<ServerPlatform> platforms)
     *
     * - Drops the player through a floating platform if down is pressed.
     */
    private void dropThroughPlatformIfRequested(List<ServerPlatform> platforms) {
        if (!input.downPressed || verticalVelocity != 0.0) {
            return;
        }
        ServerPlatform standingPlatform = findStandingPlatform(platforms, false);
        if (standingPlatform == null || !standingPlatform.passThrough()) {
            return;
        }
        y += 2.0;
        groundY = SCREEN_HEIGHT - SIZE;
        verticalVelocity = 1.0;
    }

    /*
     * landOnPlatformIfFalling(List<ServerPlatform> platforms, double previousBottom, boolean dropRequested)
     *
     * - Detects when the player lands on a platform and snaps them to it.
     */
    private void landOnPlatformIfFalling(List<ServerPlatform> platforms, double previousBottom, boolean dropRequested) {
        ServerPlatform standingPlatform = findStandingPlatform(platforms, dropRequested);
        if (standingPlatform != null && verticalVelocity == 0.0) {
            groundY = standingPlatform.top() - SIZE;
            y = groundY;
            return;
        }
        if (verticalVelocity < 0.0) {
            return;
        }
        for (ServerPlatform platform : platforms) {
            if (dropRequested && platform.passThrough()) {
                continue;
            }
            if (isLandingOn(platform, previousBottom)) {
                y = platform.top() - SIZE;
                groundY = y;
                verticalVelocity = 0.0;
                return;
            }
        }
        groundY = SCREEN_HEIGHT - SIZE;
    }

    private ServerPlatform findStandingPlatform(List<ServerPlatform> platforms, boolean dropRequested) {
        for (ServerPlatform platform : platforms) {
            if (dropRequested && platform.passThrough()) {
                continue;
            }
            if (isStandingOn(platform)) {
                return platform;
            }
        }
        return null;
    }

    private boolean isLandingOn(ServerPlatform platform, double previousBottom) {
        double currentBottom = getBottom();
        boolean crossesTop = previousBottom <= platform.top() && currentBottom >= platform.top();
        boolean overlapsHorizontally = getRight() > platform.left() && x < platform.right();
        return crossesTop && overlapsHorizontally;
    }

    private boolean isStandingOn(ServerPlatform platform) {
        boolean feetOnTop = Math.abs(getBottom() - platform.top()) < 1.0;
        boolean overlapsHorizontally = getRight() > platform.left() && x < platform.right();
        return feetOnTop && overlapsHorizontally;
    }

    /*
     * overlapsMeleeAttack(ServerPlayer target)
     *
     * - Checks if the target is within this player's melee hitbox.
     */
    private boolean overlapsMeleeAttack(ServerPlayer target) {
        double attackLeft = facingLeft ? x - 48.0 : x;
        double attackRight = facingLeft ? x + SIZE : x + SIZE + 48.0;
        return attackRight > target.x
                && attackLeft < target.getRight()
                && getBottom() > target.y
                && y < target.getBottom();
    }

    /*
     * overlapsRangedAttack(ServerPlayer target)
     *
     * - Checks if the target is within a forward cone for ranged attacks.
     */
    private boolean overlapsRangedAttack(ServerPlayer target) {
        double attackerCenterX = x + SIZE / 2.0;
        double targetCenterX = target.x + SIZE / 2.0;
        double targetCenterY = target.y + SIZE / 2.0;
        double direction = facingLeft ? -1.0 : 1.0;
        double horizontalDistance = (targetCenterX - attackerCenterX) * direction;
        double verticalDistance = Math.abs(targetCenterY - (y + SIZE / 2.0));
        return horizontalDistance >= 0.0 && horizontalDistance <= 360.0 && verticalDistance <= 120.0;
    }

    /*
     * updateAnimation(double deltaSeconds)
     *
     * - Advances the animation frame timer and calls advanceFrame when a frame completes.
     */
    private void updateAnimation(double deltaSeconds) {
        if (actionLocked) {
            actionElapsedSeconds += deltaSeconds;
        }
        if (frameCount(action) <= 1) {
            return;
        }
        frameTimer += deltaSeconds;
        while (frameTimer >= frameDuration(action, frameIndex)) {
            frameTimer -= frameDuration(action, frameIndex);
            advanceFrame();
        }
    }

    /*
     * updateJumpFrame()
     *
     * - Sets the jump animation frame based on vertical velocity and takeoff/landing state.
     */
    private void updateJumpFrame() {
        if (!"JUMP".equals(action) || actionLocked) {
            return;
        }
        if (landingFrameRemaining > 0.0) {
            frameIndex = 3;
            return;
        }
        if (!isOnGround()) {
            if (jumpElapsedSeconds < JUMP_TAKEOFF_FRAME_SECONDS) {
                frameIndex = 0;
            } else if (verticalVelocity < 0.0) {
                frameIndex = characterIndex == 2 ? 2 : 1;
            } else {
                frameIndex = 2;
            }
        }
    }

    /*
     * updateDash(double deltaSeconds)
     *
     * - Handles Tikbalang dash velocity for MOVE_3 and SPECIAL, decays over time.
     */
    private void updateDash(double deltaSeconds) {
        if (characterIndex != 0) {
            dashVelocity = 0.0;
            committedDashDirection = 0;
            specialDashPending = false;
            return;
        }

        if (specialDashPending) {
            if ("SPECIAL".equals(action) && frameIndex > 0) {
                dashVelocity = committedDashDirection * DASH_VELOCITY;
                specialDashPending = false;
            } else {
                return;
            }
        }

        if (committedDashDirection != 0 && actionLocked
                && ("MOVE_3".equals(action) || "SPECIAL".equals(action))) {
            dashVelocity = committedDashDirection * DASH_VELOCITY;
            return;
        }

        committedDashDirection = 0;
        specialDashPending = false;
        if (dashVelocity > 0.0) {
            dashVelocity = Math.max(0.0, dashVelocity - DASH_DECAY * deltaSeconds);
        } else if (dashVelocity < 0.0) {
            dashVelocity = Math.min(0.0, dashVelocity + DASH_DECAY * deltaSeconds);
        }
    }

    /*
     * advanceFrame()
     *
     * - Moves to the next frame; loops WALK, holds DEATH at last frame, ends other locked actions.
     */
    private void advanceFrame() {
        if (frameIndex < frameCount(action) - 1) {
            frameIndex++;
            return;
        }
        if ("WALK".equals(action)) {
            frameIndex = 0;
            return;
        }
        if (actionLocked && !"DEATH".equals(action)) {
            actionLocked = false;
            attackPending = false;
            vineRootPending = false;
            play("IDLE");
        }
    }

    /*
     * play(String nextAction)
     *
     * - Switches to a new animation if not already playing it.
     */
    private void play(String nextAction) {
        if (action.equals(nextAction)) {
            return;
        }
        action = nextAction;
        frameIndex = 0;
        frameTimer = 0.0;
        actionElapsedSeconds = 0.0;
        actionLocked = false;
    }

    /*
     * playOnce(String nextAction)
     *
     * - Starts a one-shot locked animation (used for abilities and death).
     */
    private void playOnce(String nextAction) {
        action = nextAction;
        frameIndex = 0;
        frameTimer = 0.0;
        actionElapsedSeconds = 0.0;
        actionLocked = true;
    }

    private int frameCount(String nextAction) {
        return switch (nextAction) {
            case "WALK", "JUMP", "MOVE_1", "MOVE_2", "MOVE_3", "SPECIAL", "DEATH" -> 4;
            default -> 1;
        };
    }

    private double frameDuration(String nextAction, int index) {
        if ("MOVE_1".equals(nextAction)) {
            return 0.10;
        }
        if ("MOVE_2".equals(nextAction)) {
            return 0.14;
        }
        if ("MOVE_3".equals(nextAction)) {
            return frameDurationMove3(index);
        }
        if ("SPECIAL".equals(nextAction)) {
            return frameDurationSpecial(index);
        }
        if ("DEATH".equals(nextAction)) {
            return 0.16;
        }
        return 0.12;
    }

    private double frameDurationMove3(int index) {
        return switch (characterIndex) {
            case 2 -> index == 2 ? 0.42 : 0.10;
            case 3 -> index == 1 ? 0.48 : 0.12;
            default -> 0.10;
        };
    }

    private double frameDurationSpecial(int index) {
        return switch (characterIndex) {
            case 0 -> index == 0 ? 0.35 : 0.20;
            case 1 -> index == 2 ? 0.60 : 0.18;
            case 2 -> index == 1 ? 0.48 : 0.18;
            case 3 -> index == 1 ? 0.48 : 0.12;
            default -> index == 2 ? 0.55 : 0.18;
        };
    }

    private void tickCooldowns(double deltaSeconds) {
        move1CooldownRemaining = tickCooldown(move1CooldownRemaining, deltaSeconds);
        move2CooldownRemaining = tickCooldown(move2CooldownRemaining, deltaSeconds);
        move3CooldownRemaining = tickCooldown(move3CooldownRemaining, deltaSeconds);
        specialCooldownRemaining = tickCooldown(specialCooldownRemaining, deltaSeconds);
    }

    private double tickCooldown(double cooldownRemaining, double deltaSeconds) {
        return Math.max(0.0, cooldownRemaining - deltaSeconds);
    }

    private boolean isMovementLocked() {
        return rootedSecondsRemaining > 0.0
                || (actionLocked && ("MOVE_2".equals(action) || "MOVE_3".equals(action) || "SPECIAL".equals(action)));
    }

    private void tickRoot(double deltaSeconds) {
        if (rootedSecondsRemaining > 0.0) {
            rootedSecondsRemaining = Math.max(0.0, rootedSecondsRemaining - deltaSeconds);
        }
    }

    void applyRoot(double seconds) {
        rootedSecondsRemaining = Math.max(rootedSecondsRemaining, seconds);
    }

    boolean hasVineRootReady() {
        return vineRootPending && actionElapsedSeconds >= ATTACK_IMPACT_SECONDS;
    }

    void markVineRootResolved() {
        vineRootPending = false;
    }

    /*
     * overlapsVineRoot(ServerPlayer target)
     *
     * - Checks if the vine root hitbox (placed in front of the caster) overlaps the target.
     */
    boolean overlapsVineRoot(ServerPlayer target) {
        double vineX = facingLeft ? x - VINE_ROOT_SIZE : x + SIZE;
        double vineRight = vineX + VINE_ROOT_SIZE;
        double vineTop = y;
        double vineBottom = y + SIZE;
        return vineRight > target.x
                && vineX < target.getRight()
                && vineBottom > target.y
                && vineTop < target.getBottom();
    }

    double getVineRootDuration() {
        return VINE_ROOT_DURATION;
    }

    double getRootedSecondsRemaining() {
        return rootedSecondsRemaining;
    }

    boolean isPendingPoison() {
        return pendingPoison;
    }

    /*
     * applyPoison(int ownerId)
     *
     * - Adds a new poison effect that deals periodic damage over time.
     */
    void applyPoison(int ownerId) {
        poisonEffects.add(new PoisonEffect(ownerId, POISON_DAMAGE_PER_TICK,
                POISON_TICK_INTERVAL, POISON_DURATION));
    }

    /*
     * tickPoisons(double deltaSeconds)
     *
     * - Ticks all active poisons, applying damage per interval. Returns the killer's ID if poison kills.
     */
    int tickPoisons(double deltaSeconds) {
        int killerOwnerId = -1;
        Iterator<PoisonEffect> iter = poisonEffects.iterator();
        while (iter.hasNext()) {
            PoisonEffect effect = iter.next();
            double elapsed = Math.min(deltaSeconds, effect.remaining);
            effect.remaining -= elapsed;
            effect.tickTimer -= elapsed;
            while (effect.tickTimer <= 0.0 && !isDead()) {
                health = Math.max(0.0, health - effect.damagePerTick);
                hitFlashSecondsRemaining = HIT_FLASH_SECONDS;
                if (health == 0.0) {
                    killerOwnerId = effect.ownerId;
                    action = "DEATH";
                    frameIndex = 0;
                    frameTimer = 0.0;
                    actionLocked = true;
                    respawnTimerRemaining = RESPAWN_SECONDS;
                    poisonEffects.clear();
                    return killerOwnerId;
                }
                effect.tickTimer += effect.tickInterval;
            }
            if (effect.remaining <= 0.0) {
                iter.remove();
            }
        }
        return killerOwnerId;
    }

    private boolean isOnGround() {
        return y >= groundY && verticalVelocity == 0.0;
    }

    boolean isDead() {
        return health <= 0.0;
    }

    int getId() {
        return id;
    }

    double getX() {
        return x;
    }

    double getY() {
        return y;
    }

    /*
     * createProjectile()
     *
     * - Creates a ServerProjectile based on the current character's attack and facing direction.
     */
    ServerProjectile createProjectile() {
        int dir = facingLeft ? -1 : 1;
        int projSize;
        double projX;
        double projY;

        if (characterIndex == 3 && "SPECIAL".equals(action)) {
            projSize = 128;
            projX = facingLeft ? x - projSize + 24.0 : x + SIZE - 24.0;
            projY = y + SIZE - projSize + 12.0;
        } else if (characterIndex == 1) {
            projSize = 48;
            projX = facingLeft ? x - 24.0 : x + SIZE - 24.0;
            projY = y + SIZE - 42.0;
        } else {
            projSize = 48;
            projX = facingLeft ? x - 24.0 : x + SIZE - 24.0;
            projY = y + SIZE - 58.0;
        }

        return new ServerProjectile(id, projX, projY, dir, projSize, pendingDamage);
    }

    private double getRight() {
        return x + SIZE;
    }

    private double getBottom() {
        return y + SIZE;
    }

    private void keepInsideScreen() {
        x = clamp(x, 0.0, SCREEN_WIDTH - SIZE);
        groundY = clamp(groundY, 0.0, SCREEN_HEIGHT - SIZE);
        y = Math.min(y, groundY);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    /*
     * PoisonEffect
     *
     * - Tracks a single poison instance: owner, damage per tick, interval, and remaining duration.
     */
    private static final class PoisonEffect {
        final int ownerId;
        final double damagePerTick;
        final double tickInterval;
        double remaining;
        double tickTimer;

        PoisonEffect(int ownerId, double damagePerTick, double tickInterval, double duration) {
            this.ownerId = ownerId;
            this.damagePerTick = damagePerTick;
            this.tickInterval = tickInterval;
            this.remaining = duration;
            this.tickTimer = tickInterval;
        }
    }
}
