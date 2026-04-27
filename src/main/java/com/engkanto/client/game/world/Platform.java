package com.engkanto.client.game.world;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

public final class Platform {
    private static final Color TOP_COLOR = new Color(105, 84, 55);
    private static final Color BODY_COLOR = new Color(73, 58, 42);
    private static final int TOP_EDGE_HEIGHT = 6;

    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public Platform(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void draw(Graphics2D graphics) {
        graphics.setColor(BODY_COLOR);
        graphics.fill(new Rectangle2D.Double(x, y, width, height));

        graphics.setColor(TOP_COLOR);
        graphics.fill(new Rectangle2D.Double(x, y, width, TOP_EDGE_HEIGHT));
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public double getTop() {
        return y;
    }

    public double getLeft() {
        return x;
    }

    public double getRight() {
        return x + width;
    }
}
