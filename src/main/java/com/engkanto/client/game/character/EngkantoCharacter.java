package com.engkanto.client.game.character;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.engkanto.client.game.entity.Player;
import com.engkanto.client.game.entity.Projectile;
import com.engkanto.client.render.AssetLoader;
import com.engkanto.client.render.SpriteSheet;

public final class EngkantoCharacter extends SpriteCharacter {
    private static final int MOVE_1_PROJECTILE_SOURCE_X = 606;
    private static final int MOVE_1_PROJECTILE_SOURCE_Y = 556;
    private static final int MOVE_2_PROJECTILE_SOURCE_X = 550;
    private static final int MOVE_2_PROJECTILE_SOURCE_Y = 770;
    private static final int PROJECTILE_SOURCE_SIZE = 130;
    private static final int SPECIAL_PROJECTILE_DRAW_SIZE = 128;
    private static final double MOVE_1_COOLDOWN_SECONDS = 0.20;
    private static final double MOVE_1_PROJECTILE_DAMAGE = 10.0;
    private static final double MOVE_2_PROJECTILE_DAMAGE = 20.0;
    private static final double SPECIAL_PROJECTILE_DAMAGE = 25.0;
    private static final int VINE_DRAW_SIZE = 96;
    private static final double VINE_SECONDS = 0.55;

    private final BufferedImage move1ProjectileImage;
    private final BufferedImage move2ProjectileImage;
    private final BufferedImage vineImage;
    private final BufferedImage specialProjectileImage;
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Vine> vines = new ArrayList<>();
    private boolean move1ProjectilePending;
    private boolean move2ProjectilePending;
    private boolean vinePending;
    private boolean specialProjectilePending;

    public EngkantoCharacter() {
        super("Engkanto", "/assets/sprites/engkanto.png");
        BufferedImage engkantoSheet = SpriteSheet.removeWhiteBackground(AssetLoader.loadImage("/assets/sprites/engkanto.png"));
        move1ProjectileImage = engkantoSheet.getSubimage(
                MOVE_1_PROJECTILE_SOURCE_X,
                MOVE_1_PROJECTILE_SOURCE_Y,
                PROJECTILE_SOURCE_SIZE,
                PROJECTILE_SOURCE_SIZE
        );
        move2ProjectileImage = engkantoSheet.getSubimage(
                MOVE_2_PROJECTILE_SOURCE_X,
                MOVE_2_PROJECTILE_SOURCE_Y,
                PROJECTILE_SOURCE_SIZE,
                PROJECTILE_SOURCE_SIZE
        );
        vineImage = super.getFrame(PlayerAction.MOVE_3, 2);
        specialProjectileImage = super.getFrame(PlayerAction.SPECIAL, 2);
    }

    @Override
    public BufferedImage getFrame(PlayerAction action, int frameIndex) {
        if ((action == PlayerAction.MOVE_3 || action == PlayerAction.SPECIAL) && frameIndex == 2) {
            return super.getFrame(action, 1);
        }
        return super.getFrame(action, frameIndex);
    }

    @Override
    public double getFrameDuration(PlayerAction action, int frameIndex) {
        if (action == PlayerAction.MOVE_3 || action == PlayerAction.SPECIAL) {
            return frameIndex == 1 ? 0.48 : 0.12;
        }
        return super.getFrameDuration(action, frameIndex);
    }

    @Override
    public void update(Player player, double deltaSeconds) {
        updateMove1Projectile(player);
        updateMove2Projectile(player);
        updateVine(player);
        updateSpecialProjectile(player);
        updateProjectiles(deltaSeconds);
        updateVines(deltaSeconds);
    }

    @Override
    public void drawEffects(Graphics2D graphics) {
        for (Vine vine : vines) {
            vine.draw(graphics);
        }
        for (Projectile projectile : projectiles) {
            projectile.draw(graphics);
        }
    }

    @Override
    public void onMove1(Player player) {
        move1ProjectilePending = true;
    }

    @Override
    public void onMove2(Player player) {
        move2ProjectilePending = true;
    }

    @Override
    public void onMove3(Player player) {
        vinePending = true;
    }

    @Override
    public void onSpecial(Player player) {
        specialProjectilePending = true;
    }

    @Override
    public void cancelMovementEffect() {
        move1ProjectilePending = false;
        move2ProjectilePending = false;
        vinePending = false;
        specialProjectilePending = false;
    }

    @Override
    public boolean locksMovement(PlayerAction action) {
        return action == PlayerAction.MOVE_2
                || action == PlayerAction.MOVE_3
                || action == PlayerAction.SPECIAL;
    }

    @Override
    public boolean canDirectAttackHit(PlayerAction action, int frameIndex) {
        return action != PlayerAction.MOVE_1
                && action != PlayerAction.MOVE_2
                && action != PlayerAction.SPECIAL;
    }

    @Override
    public double getCooldown(PlayerAction action, double defaultCooldownSeconds) {
        if (action == PlayerAction.MOVE_1) {
            return MOVE_1_COOLDOWN_SECONDS;
        }
        return defaultCooldownSeconds;
    }

    private void updateMove1Projectile(Player player) {
        if (!move1ProjectilePending || player.getCurrentAction() != PlayerAction.MOVE_1 || player.getCurrentFrameIndex() < 2) {
            return;
        }

        addProjectile(move1ProjectileImage, player, MOVE_1_PROJECTILE_DAMAGE);
        move1ProjectilePending = false;
    }

    private void updateMove2Projectile(Player player) {
        if (!move2ProjectilePending || player.getCurrentAction() != PlayerAction.MOVE_2 || player.getCurrentFrameIndex() < 2) {
            return;
        }

        addProjectile(move2ProjectileImage, player, MOVE_2_PROJECTILE_DAMAGE);
        move2ProjectilePending = false;
    }

    private void updateVine(Player player) {
        if (!vinePending || player.getCurrentAction() != PlayerAction.MOVE_3 || player.getCurrentFrameIndex() < 2) {
            return;
        }

        double vineX = player.isFacingLeft() ? player.getX() - VINE_DRAW_SIZE : player.getX() + Player.SIZE;
        vines.add(new Vine(vineImage, vineX, player.getY(), player.isFacingLeft(), VINE_SECONDS));
        vinePending = false;
    }

    private void updateSpecialProjectile(Player player) {
        if (!specialProjectilePending || player.getCurrentAction() != PlayerAction.SPECIAL || player.getCurrentFrameIndex() < 2) {
            return;
        }

        addLargeProjectile(specialProjectileImage, player);
        specialProjectilePending = false;
    }

    private void addProjectile(BufferedImage image, Player player, double damage) {
        int direction = player.isFacingLeft() ? -1 : 1;
        double projectileX = player.isFacingLeft() ? player.getX() - 24.0 : player.getX() + Player.SIZE - 24.0;
        double projectileY = player.getY() + Player.SIZE - 58.0;
        projectiles.add(new Projectile(image, projectileX, projectileY, direction, Projectile.DRAW_SIZE, damage));
    }

    private void addLargeProjectile(BufferedImage image, Player player) {
        int direction = player.isFacingLeft() ? -1 : 1;
        double projectileX = player.isFacingLeft()
                ? player.getX() - SPECIAL_PROJECTILE_DRAW_SIZE + 24.0
                : player.getX() + Player.SIZE - 24.0;
        double projectileY = player.getY() + Player.SIZE - SPECIAL_PROJECTILE_DRAW_SIZE + 12.0;
        projectiles.add(new Projectile(
                image,
                projectileX,
                projectileY,
                direction,
                SPECIAL_PROJECTILE_DRAW_SIZE,
                SPECIAL_PROJECTILE_DAMAGE
        ));
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

    private void updateVines(double deltaSeconds) {
        Iterator<Vine> iterator = vines.iterator();
        while (iterator.hasNext()) {
            Vine vine = iterator.next();
            vine.update(deltaSeconds);
            if (!vine.isActive()) {
                iterator.remove();
            }
        }
    }

    private static final class Vine {
        private final BufferedImage image;
        private final double x;
        private final double y;
        private final boolean facingLeft;
        private double secondsRemaining;

        private Vine(BufferedImage image, double x, double y, boolean facingLeft, double secondsRemaining) {
            this.image = image;
            this.x = x;
            this.y = y;
            this.facingLeft = facingLeft;
            this.secondsRemaining = secondsRemaining;
        }

        private void update(double deltaSeconds) {
            secondsRemaining = Math.max(0.0, secondsRemaining - deltaSeconds);
        }

        private void draw(Graphics2D graphics) {
            if (facingLeft) {
                graphics.drawImage(image, (int) x + VINE_DRAW_SIZE, (int) y, -VINE_DRAW_SIZE, VINE_DRAW_SIZE, null);
            } else {
                graphics.drawImage(image, (int) x, (int) y, VINE_DRAW_SIZE, VINE_DRAW_SIZE, null);
            }
        }

        private boolean isActive() {
            return secondsRemaining > 0.0;
        }
    }

    @Override
    public List<Projectile> getProjectiles() {
        return projectiles;
    }
}
