package com.engkanto.client.game.character;

import com.engkanto.client.render.AssetLoader;
import com.engkanto.client.render.SpriteSheet;

import java.awt.image.BufferedImage;

public abstract class SpriteCharacter implements CharacterDefinition {
    private static final int SHEET_COLUMNS = 4;
    private static final int SHEET_ROWS = 7;

    private final String name;
    private final SpriteSheet spriteSheet;

    protected SpriteCharacter(String name, String spritePath) {
        this.name = name;
        this.spriteSheet = new SpriteSheet(
                AssetLoader.loadImage(spritePath),
                0,
                0,
                SHEET_COLUMNS,
                SHEET_ROWS
        );
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public BufferedImage getFrame(PlayerAction action, int frameIndex) {
        return spriteSheet.getFrame(action.getRow(), frameIndex);
    }

    @Override
    public double getFrameDuration(PlayerAction action, int frameIndex) {
        return action.getFrameDuration(frameIndex);
    }
}
