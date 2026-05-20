/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file loads image assets from the resources folder. It gives the game a single helper for reading sprites, logos, tiles, and backgrounds.
 */
package com.engkanto.client.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public final class AssetLoader {
    private AssetLoader() {
    }

    public static BufferedImage loadImage(String resourcePath) {
        try (InputStream inputStream = AssetLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing asset: " + resourcePath);
            }
            return ImageIO.read(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load asset: " + resourcePath, exception);
        }
    }
}
