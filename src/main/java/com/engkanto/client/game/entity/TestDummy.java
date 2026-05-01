package com.engkanto.client.game.entity;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.RoundRectangle2D;

import com.engkanto.client.game.combat.DamageNumberEmitter;
import com.engkanto.client.game.combat.HealthComponent;
import com.engkanto.client.game.combat.HealthListener;

public final class TestDummy {
    public  static final int WIDTH  = 48;
    public  static final int HEIGHT = 72;

    private static final Color COLOR_BODY_ALIVE      = new Color(190, 140, 90);
    private static final Color COLOR_BODY_DEAD        = new Color(110,  80, 60);
    private static final Color COLOR_BODY_HIT         = new Color(255, 200, 160);
    private static final Color COLOR_OUTLINE          = new Color( 60,  35, 20);
    private static final Color COLOR_POLE             = new Color( 80,  50, 30);
    private static final Color COLOR_HEALTH_BG        = new Color( 40,  10, 10, 200);
    private static final Color COLOR_HEALTH_FILL      = new Color(210,  50, 50);
    private static final Color COLOR_HEALTH_LOW       = new Color(230, 120,  30);
    private static final Color COLOR_HEALTH_BORDER    = new Color( 20,   5,  5, 220);
    private static final Color COLOR_LABEL            = new Color(240, 220, 180);
    private static final Color COLOR_DEAD_LABEL       = new Color(180, 100,  80);

    private static final Font  LABEL_FONT        = new Font("SansSerif", Font.BOLD, 10);
    private static final int   HEALTH_BAR_WIDTH  = 52;
    private static final int   HEALTH_BAR_HEIGHT = 6;
    private static final int   HEALTH_BAR_OFFSET_Y = -10;

    private static final double HIT_FLASH_DURATION = 0.12;  
    private static final double RESPAWN_SECONDS     = 4.0; 

    private final double originX;
    private final double originY;

    private final HealthComponent health;
    private final DamageNumberEmitter emitter;

    private double hitFlashRemaining;
    private double respawnTimer;
    private boolean pendingRespawn;

    public TestDummy(double x, double y) {
        this.originX = x;
        this.originY = y;
        this.health = new HealthComponent(100.0);
        this.emitter = new DamageNumberEmitter();

        health.addListener(new HealthListener() {
            @Override
            public void onDamage(double damage) {
                hitFlashRemaining = HIT_FLASH_DURATION;
                emitter.emit(originX + WIDTH / 2.0, originY - 4, damage);
            }

            @Override
            public void onHeal(double amount) {
                //listener requirement
            }

            @Override
            public void onDeath() {
                hitFlashRemaining = 0.0;
                pendingRespawn    = true;
                respawnTimer      = RESPAWN_SECONDS;
            }
        });
    }

    public void takeDamage(double damage) {
        health.takeDamage(damage);
    }

    public void update(double deltaSeconds) {
        health.update(deltaSeconds);
        emitter.update(deltaSeconds);

        if (hitFlashRemaining > 0.0) {
            hitFlashRemaining = Math.max(0.0, hitFlashRemaining - deltaSeconds);
        }

        if (pendingRespawn) {
            respawnTimer -= deltaSeconds;
            if (respawnTimer <= 0.0) {
                respawn();
            }
        }
    }

    public void draw(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawPole(graphics);
        drawBody(graphics);
        drawHealthBar(graphics);
        drawLabel(graphics);
        emitter.draw(graphics);
    }

    public HealthComponent getHealthComponent() {
        return health;
    }

    public double getX()      { return originX; }
    public double getY()      { return originY; }
    public double getLeft()   { return originX; }
    public double getRight()  { return originX + WIDTH; }
    public double getTop()    { return originY; }
    public double getBottom() { return originY + HEIGHT; }
    public double getCenterX(){ return originX + WIDTH  / 2.0; }

    public boolean overlapsHitbox(double left, double top, double right, double bottom) {
        return right > getLeft()
                && left < getRight()
                && bottom > getTop()
                && top < getBottom();
    }

    private void drawPole(Graphics2D graphics) {
        int poleX     = (int) originX + WIDTH / 2 - 3;
        int poleWidth = 6;
        int poleTop   = (int) originY + HEIGHT - 12;
        int poleBot   = (int) originY + HEIGHT + 4;  

        graphics.setColor(COLOR_POLE);
        graphics.fillRect(poleX, poleTop, poleWidth, poleBot - poleTop);
        graphics.setColor(COLOR_OUTLINE);
        graphics.drawRect(poleX, poleTop, poleWidth, poleBot - poleTop);
    }

    private void drawBody(Graphics2D graphics) {
        int x = (int) originX;
        int y = (int) originY;

        Color bodyColor;
        if (health.isDead()) {
            bodyColor = COLOR_BODY_DEAD;
        } else if (hitFlashRemaining > 0.0) {
            bodyColor = COLOR_BODY_HIT;
        } else {
            bodyColor = COLOR_BODY_ALIVE;
        }

        RoundRectangle2D body = new RoundRectangle2D.Double(x, y, WIDTH, HEIGHT - 12, 12, 12);
        graphics.setColor(bodyColor);
        graphics.fill(body);

        Stroke previousStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.setColor(COLOR_OUTLINE.brighter());
        graphics.drawLine(x + WIDTH / 2, y + 8, x + WIDTH / 2, y + HEIGHT - 20);
        graphics.drawLine(x + 10,        y + HEIGHT / 2 - 6, x + WIDTH - 10, y + HEIGHT / 2 - 6);
        graphics.setStroke(previousStroke);

        graphics.setColor(COLOR_OUTLINE);
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.draw(body);
        graphics.setStroke(previousStroke);

        int headDiameter = 22;
        int headX = x + WIDTH / 2 - headDiameter / 2;
        int headY = y - headDiameter / 2 + 4;
        graphics.setColor(bodyColor);
        graphics.fillOval(headX, headY, headDiameter, headDiameter);
        graphics.setColor(COLOR_OUTLINE);
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.drawOval(headX, headY, headDiameter, headDiameter);
        graphics.setStroke(previousStroke);
    }

    private void drawHealthBar(Graphics2D graphics) {
        int barX = (int) originX + WIDTH / 2 - HEALTH_BAR_WIDTH / 2;
        int barY = (int) originY + HEALTH_BAR_OFFSET_Y;

        graphics.setColor(COLOR_HEALTH_BG);
        graphics.fillRoundRect(barX - 1, barY - 1, HEALTH_BAR_WIDTH + 2, HEALTH_BAR_HEIGHT + 2, 4, 4);

        double fraction  = health.getHealthPercentage();
        int    fillWidth = (int) (HEALTH_BAR_WIDTH * fraction);
        Color  fillColor = fraction <= 0.30 ? COLOR_HEALTH_LOW : COLOR_HEALTH_FILL;
        if (fillWidth > 0) {
            graphics.setColor(fillColor);
            graphics.fillRoundRect(barX, barY, fillWidth, HEALTH_BAR_HEIGHT, 3, 3);
        }

        graphics.setColor(COLOR_HEALTH_BORDER);
        Stroke previousStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.drawRoundRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT, 4, 4);
        graphics.setStroke(previousStroke);
    }

    private void drawLabel(Graphics2D graphics) {
        String label;
        Color  labelColor;

        if (health.isDead()) {
            long secondsLeft = (long) Math.ceil(respawnTimer);
            label      = "DEAD (" + secondsLeft + "s)";
            labelColor = COLOR_DEAD_LABEL;
        } else {
            label      = "DUMMY";
            labelColor = COLOR_LABEL;
        }

        graphics.setFont(LABEL_FONT);
        graphics.setColor(labelColor);
        FontMetrics fm        = graphics.getFontMetrics();
        int         textWidth = fm.stringWidth(label);
        int         drawX     = (int) originX + WIDTH / 2 - textWidth / 2;
        int         drawY     = (int) originY - 14;
        graphics.drawString(label, drawX, drawY);
    }

    private void respawn() {
        health.revive();
        pendingRespawn    = false;
        hitFlashRemaining = 0.0;
    }
}
