package com.engkanto.client.game.character;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.engkanto.client.game.combat.DamageComponent;
import com.engkanto.client.game.combat.HealthComponent;
import com.engkanto.client.game.entity.Player;
import com.engkanto.client.game.entity.Projectile;
import com.engkanto.client.render.AssetLoader;
import com.engkanto.client.render.SpriteSheet;

public final class KapreCharacter extends SpriteCharacter {
    private static final int LOG_SOURCE_X = 610;
    private static final int LOG_SOURCE_Y = 1035;
    private static final int LOG_SOURCE_SIZE = 150;
    private static final int THROW_LAST_FRAME_LEFT_TRIM = 42;
    private static final double MOVE_1_DAMAGE = 15.0;
    private static final double MOVE_2_COOLDOWN_SECONDS = 1.0;

    private final BufferedImage logImage;
    private final List<Projectile> projectiles = new ArrayList<>();
    private boolean logThrowPending;

    public KapreCharacter() {
        super("Kapre", "/assets/sprites/kapre.png");
        BufferedImage kapreSheet = SpriteSheet.removeWhiteBackground(AssetLoader.loadImage("/assets/sprites/kapre.png"));
        logImage = kapreSheet.getSubimage(LOG_SOURCE_X, LOG_SOURCE_Y, LOG_SOURCE_SIZE, LOG_SOURCE_SIZE);
    }

    @Override
    public BufferedImage getFrame(PlayerAction action, int frameIndex) {
        BufferedImage frame = super.getFrame(action, frameIndex);
        if (action == PlayerAction.MOVE_3 && frameIndex == 3) {
            return frame.getSubimage(
                    THROW_LAST_FRAME_LEFT_TRIM,
                    0,
                    frame.getWidth() - THROW_LAST_FRAME_LEFT_TRIM,
                    frame.getHeight()
            );
        }
        return frame;
    }

    @Override
    public double getFrameDuration(PlayerAction action, int frameIndex) {
        if (action == PlayerAction.SPECIAL) {
            return frameIndex == 2 ? 0.55 : 0.18;
        }
        return super.getFrameDuration(action, frameIndex);
    }

    @Override
    public void update(Player player, double deltaSeconds) {
        updateLogThrow(player);
        updateProjectiles(deltaSeconds);
    }

    @Override
    public void drawEffects(Graphics2D graphics) {
        for (Projectile projectile : projectiles) {
            projectile.draw(graphics);
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
        logThrowPending = true;
    }

    @Override
    public void onSpecial(Player player) {
        cancelMovementEffect();
    }

    @Override
    public void cancelMovementEffect() {
        logThrowPending = false;
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

    private void updateLogThrow(Player player) {
        if (!logThrowPending || player.getCurrentAction() != PlayerAction.MOVE_3 || player.getCurrentFrameIndex() < 2) {
            return;
        }

        int direction = player.isFacingLeft() ? -1 : 1;
        double projectileX = player.isFacingLeft() ? player.getX() - 24.0 : player.getX() + Player.SIZE - 24.0;
        double projectileY = player.getY() + Player.SIZE - 42.0;
        projectiles.add(new Projectile(logImage, projectileX, projectileY, direction));
        logThrowPending = false;
    }

    private void updateProjectiles(double deltaSeconds) {
        Iterator<Projectile> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            Projectile projectile = iterator.next();
            projectile.update(deltaSeconds);
            if (!projectile.isActive()) {
                iterator.remove();
            }
        }
    }

    @Override
    public List<Projectile> getProjectiles() {
        return projectiles;
    }
}
