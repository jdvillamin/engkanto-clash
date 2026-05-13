package com.engkanto.server.game;

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
    private static final double DASH_VELOCITY = 540.0;
    private static final double DASH_DECAY = 1_800.0;

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
    private double dashVelocity;
    private int committedDashDirection;
    private boolean specialDashPending;
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

    void update(double deltaSeconds, List<ServerPlatform> platforms) {
        if (isDead()) {
            updateDeath(deltaSeconds);
            return;
        }

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
        }

        dropThroughPlatformIfRequested(platforms);
        updateJump(deltaSeconds);
        updateDash(deltaSeconds);
        x += (movementVelocity + dashVelocity) * deltaSeconds;
        landOnPlatformIfFalling(platforms, previousBottom, input.downPressed);
        updateAnimation(deltaSeconds);
        keepInsideScreen();
    }

    boolean hasAttackReady() {
        return attackPending && actionElapsedSeconds >= ATTACK_IMPACT_SECONDS;
    }

    boolean canHit(ServerPlayer target) {
        if (target == this || target.isDead()) {
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
    }

    boolean takeDamage(double damage) {
        if (damage <= 0.0 || isDead()) {
            return false;
        }
        health = Math.max(0.0, health - damage);
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
        return snapshot;
    }

    private void updateDeath(double deltaSeconds) {
        updateAnimation(deltaSeconds);
        dashVelocity = 0.0;
        committedDashDirection = 0;
        specialDashPending = false;
        respawnTimerRemaining -= deltaSeconds;
        if (respawnTimerRemaining <= 0.0) {
            health = MAX_HEALTH;
            actionLocked = false;
            play("IDLE");
        }
    }

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

    private void startLockedAction(String nextAction) {
        playOnce(nextAction);
        pendingDamage = getDamageFor(nextAction);
        pendingRangedAttack = isRangedAttack(nextAction);
        attackPending = pendingDamage > 0.0;
        if (characterIndex == 2 && "MOVE_3".equals(nextAction)) {
            health = Math.min(MAX_HEALTH, health + 25.0);
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

    private double getDamageFor(String nextAction) {
        return switch (characterIndex) {
            case 0 -> switch (nextAction) {
                case "MOVE_1" -> 15.0;
                case "MOVE_2", "SPECIAL" -> 25.0;
                default -> 0.0;
            };
            case 1 -> switch (nextAction) {
                case "MOVE_1" -> 15.0;
                case "MOVE_2", "SPECIAL" -> 25.0;
                case "MOVE_3" -> 10.0;
                default -> 0.0;
            };
            case 2 -> switch (nextAction) {
                case "MOVE_1" -> 15.0;
                case "MOVE_2" -> 25.0;
                case "SPECIAL" -> 10.0;
                default -> 0.0;
            };
            case 3 -> switch (nextAction) {
                case "MOVE_1" -> 10.0;
                case "MOVE_2" -> 20.0;
                case "SPECIAL" -> 25.0;
                default -> 0.0;
            };
            default -> 0.0;
        };
    }

    private double getCooldown(String nextAction) {
        return switch (nextAction) {
            case "MOVE_1" -> characterIndex == 3 ? 0.20 : 0.35;
            case "MOVE_2" -> characterIndex == 0 || characterIndex == 1 || characterIndex == 2 ? 1.0 : 0.65;
            case "MOVE_3" -> 1.10;
            case "SPECIAL" -> 10.0;
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

    private void updateJump(double deltaSeconds) {
        if (isOnGround() && verticalVelocity >= 0.0) {
            return;
        }
        y += verticalVelocity * deltaSeconds;
        double gravityScale = characterIndex == 2 && input.glidePressed && verticalVelocity > 0.0 ? 0.35 : 1.0;
        verticalVelocity += GRAVITY_PIXELS_PER_SECOND * gravityScale * deltaSeconds;
        if (y >= groundY) {
            y = groundY;
            verticalVelocity = 0.0;
        }
    }

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

    private boolean overlapsMeleeAttack(ServerPlayer target) {
        double attackLeft = facingLeft ? x - 48.0 : x;
        double attackRight = facingLeft ? x + SIZE : x + SIZE + 48.0;
        return attackRight > target.x
                && attackLeft < target.getRight()
                && getBottom() > target.y
                && y < target.getBottom();
    }

    private boolean overlapsRangedAttack(ServerPlayer target) {
        double attackerCenterX = x + SIZE / 2.0;
        double targetCenterX = target.x + SIZE / 2.0;
        double targetCenterY = target.y + SIZE / 2.0;
        double direction = facingLeft ? -1.0 : 1.0;
        double horizontalDistance = (targetCenterX - attackerCenterX) * direction;
        double verticalDistance = Math.abs(targetCenterY - (y + SIZE / 2.0));
        return horizontalDistance >= 0.0 && horizontalDistance <= 360.0 && verticalDistance <= 120.0;
    }

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
            play("IDLE");
        }
    }

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
        if ("MOVE_3".equals(nextAction)) {
            return 0.10;
        }
        if ("SPECIAL".equals(nextAction)) {
            return index == 2 ? 0.55 : 0.18;
        }
        if ("DEATH".equals(nextAction)) {
            return 0.16;
        }
        return 0.12;
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
        return actionLocked && ("MOVE_2".equals(action) || "MOVE_3".equals(action) || "SPECIAL".equals(action));
    }

    private boolean isOnGround() {
        return y >= groundY && verticalVelocity == 0.0;
    }

    private boolean isDead() {
        return health <= 0.0;
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
}
