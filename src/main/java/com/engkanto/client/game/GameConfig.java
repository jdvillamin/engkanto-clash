/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file stores shared game screen and timing constants. Other client game classes use it so they agree on window size, tile size, and update rate.
 */
package com.engkanto.client.game;

public final class GameConfig {
    public static final int TILE_SIZE = 32;
    public static final int SCREEN_COLUMNS = 40;
    public static final int SCREEN_ROWS = 22;
    public static final int SCREEN_WIDTH = TILE_SIZE * SCREEN_COLUMNS;
    public static final int SCREEN_HEIGHT = TILE_SIZE * SCREEN_ROWS;
    public static final int TARGET_UPDATES_PER_SECOND = 60;

    private GameConfig() {
    }
}
