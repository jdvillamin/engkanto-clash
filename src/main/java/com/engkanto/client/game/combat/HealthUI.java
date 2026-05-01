package com.engkanto.client.game.combat;

import com.engkanto.client.game.entity.Player;
import com.engkanto.client.game.combat.HealthComponent;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public class HealthUI {
    private static final int X = 20;
    private static final int Y = 20;
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 20;
    private static final int TEXT_OFFSET_X = 220;
    private static final int TEXT_OFFSET_Y = 35;
    
    private final HealthComponent health;
    private final Font font = new Font("Arial", Font.BOLD, 16);
    
    public HealthUI(Player player) {
        this.health = player.getHealthComponent();
    }
    
    public void draw(Graphics2D graphics) {
        graphics.setFont(font);
        
        double healthPercent = health.getHealthPercentage();
        
        graphics.setColor(Color.BLACK);
        graphics.fillRoundRect(X - 2, Y - 2, BAR_WIDTH + 4, BAR_HEIGHT + 4, 8, 8);
        
        graphics.setColor(Color.RED);
        graphics.fillRoundRect(X, Y, BAR_WIDTH, BAR_HEIGHT, 6, 6);
        
        graphics.setColor(getHealthColor(healthPercent));
        int healthWidth = (int) (BAR_WIDTH * healthPercent);
        graphics.fillRoundRect(X, Y, healthWidth, BAR_HEIGHT, 6, 6);
        
        graphics.setColor(Color.WHITE);
        String healthText = String.format("%.0f/%.0f", 
            health.getCurrentHealth(), health.getMaxHealth());
        graphics.drawString(healthText, TEXT_OFFSET_X, TEXT_OFFSET_Y);
    }
    
    private Color getHealthColor(double percent) {
        if (percent > 0.6) return new Color(0, 255, 0);
        if (percent > 0.3) return new Color(255, 255, 0);
        return new Color(255, 0, 0);                      
    }
}