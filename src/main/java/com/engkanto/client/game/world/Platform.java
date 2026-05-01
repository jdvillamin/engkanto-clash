package com.engkanto.client.game.world;

import com.engkanto.client.render.AssetLoader;
import com.engkanto.client.game.GameConfig;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public final class Platform {
    private static final Color TOP_COLOR = new Color(105, 84, 55);
    private static final Color BODY_COLOR = new Color(73, 58, 42);
    private static final int TOP_EDGE_HEIGHT = 6;
    private static final String GRASS_TILE_PATH = "/assets/platforms/grass.png";
    private static final String GRASS_EDGE_TILE_PATH = "/assets/platforms/grass edge.png";
    private static final String DIRT_TILE_PATH = "/assets/platforms/dirt.png";
    private static final BufferedImage GRASS_TILE = loadTile(GRASS_TILE_PATH);
    private static final BufferedImage GRASS_EDGE_TILE = loadTile(GRASS_EDGE_TILE_PATH);
    private static final BufferedImage GRASS_EDGE_TILE_RIGHT = mirrorHorizontally(GRASS_EDGE_TILE);
    private static final BufferedImage DIRT_TILE = loadTile(DIRT_TILE_PATH);

    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final Type type;

    public Platform(double x, double y, double width, double height, Type type) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
    }

    public void draw(Graphics2D graphics) {
        if (hasTiles()) {
            drawTiles(graphics);
            return;
        }

        drawFallback(graphics);
    }

    private void drawTiles(Graphics2D graphics) {
        int columns = (int) Math.ceil(width / GameConfig.TILE_SIZE);
        int rows = (int) Math.ceil(height / GameConfig.TILE_SIZE);

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                BufferedImage tile = selectTile(row, column, columns);
                if (tile == null) {
                    continue;
                }

                int drawX = (int) Math.round(x) + column * GameConfig.TILE_SIZE;
                int drawY = (int) Math.round(y) + row * GameConfig.TILE_SIZE;
                int drawWidth = Math.min(GameConfig.TILE_SIZE, (int) Math.ceil(x + width - drawX));
                int drawHeight = Math.min(GameConfig.TILE_SIZE, (int) Math.ceil(y + height - drawY));
                graphics.drawImage(
                        tile,
                        drawX,
                        drawY,
                        drawX + drawWidth,
                        drawY + drawHeight,
                        0,
                        0,
                        tile.getWidth(),
                        tile.getHeight(),
                        null
                );
            }
        }
    }

    private void drawFallback(Graphics2D graphics) {
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

    public boolean isPassThrough() {
        return type == Type.FLOATING;
    }

    private BufferedImage selectTile(int row, int column, int columns) {
        boolean isTopRow = row == 0;
        boolean isLeftEdge = column == 0;
        boolean isRightEdge = column == columns - 1;

        if (type == Type.FLOATING) {
            if (isLeftEdge) {
                return GRASS_EDGE_TILE;
            }
            if (isRightEdge) {
                return GRASS_EDGE_TILE_RIGHT;
            }
            return GRASS_TILE;
        }

        if (isTopRow) {
            if (isLeftEdge) {
                return GRASS_EDGE_TILE;
            }
            if (isRightEdge) {
                return GRASS_EDGE_TILE_RIGHT;
            }
            return GRASS_TILE;
        }

        return DIRT_TILE;
    }

    private static boolean hasTiles() {
        return GRASS_TILE != null && GRASS_EDGE_TILE != null && DIRT_TILE != null;
    }

    private static BufferedImage loadTile(String resourcePath) {
        try {
            return trimTransparentBorders(AssetLoader.loadImage(resourcePath));
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private static BufferedImage mirrorHorizontally(BufferedImage source) {
        if (source == null) {
            return null;
        }

        BufferedImage mirrored = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = mirrored.createGraphics();
        try {
            AffineTransform transform = AffineTransform.getScaleInstance(-1, 1);
            transform.translate(-source.getWidth(), 0);
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return mirrored;
    }

    private static BufferedImage trimTransparentBorders(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = (source.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            return source;
        }

        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    public enum Type {
        GROUND,
        FLOATING
    }
}
