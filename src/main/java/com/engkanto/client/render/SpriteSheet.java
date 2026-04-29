package com.engkanto.client.render;

import java.awt.image.BufferedImage;

public final class SpriteSheet {
    private static final int WHITE_BACKGROUND_THRESHOLD = 215;
    private static final int NEAR_WHITE_BRIGHTNESS_THRESHOLD = 205;
    private static final int NEAR_WHITE_COLOR_SPREAD = 45;

    private final BufferedImage image;
    private final int startX;
    private final int startY;
    private final int frameWidth;
    private final int frameHeight;

    public SpriteSheet(BufferedImage image, int startX, int startY, int columns, int rows) {
        this.image = removeWhiteBackground(image);
        this.startX = startX;
        this.startY = startY;
        this.frameWidth = (image.getWidth() - startX) / columns;
        this.frameHeight = (image.getHeight() - startY) / rows;
    }

    public BufferedImage getFrame(int row, int column) {
        int x = startX + column * frameWidth;
        int y = startY + row * frameHeight;
        return image.getSubimage(x, y, frameWidth, frameHeight);
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }

    public static BufferedImage removeWhiteBackground(BufferedImage source) {
        BufferedImage transparent = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                int red = (argb >> 16) & 0xFF;
                int green = (argb >> 8) & 0xFF;
                int blue = argb & 0xFF;

                if (isWhite(red, green, blue)) {
                    transparent.setRGB(x, y, 0x00000000);
                } else {
                    transparent.setRGB(x, y, (alpha << 24) | (argb & 0x00FFFFFF));
                }
            }
        }

        return transparent;
    }

    private static boolean isWhite(int red, int green, int blue) {
        if (red >= WHITE_BACKGROUND_THRESHOLD
                && green >= WHITE_BACKGROUND_THRESHOLD
                && blue >= WHITE_BACKGROUND_THRESHOLD) {
            return true;
        }

        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return max >= NEAR_WHITE_BRIGHTNESS_THRESHOLD && max - min <= NEAR_WHITE_COLOR_SPREAD;
    }
}
