package com.engkanto.client.render;

import com.engkanto.client.game.GameConfig;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public final class DebugRenderer {
    private static final Font HUD_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);

    public void drawHud(Graphics2D graphics) {
        graphics.setFont(HUD_FONT);
        graphics.setColor(new Color(242, 232, 194));
        graphics.drawString("Character Sprite Test", 16, 24);
        graphics.drawString("P switch | A/D walk | W jump | S idle | J/K/E moves | L ult | Z death", 16, GameConfig.SCREEN_HEIGHT - 16);
    }
}
