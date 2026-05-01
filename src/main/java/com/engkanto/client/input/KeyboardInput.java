package com.engkanto.client.input;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public final class KeyboardInput extends KeyAdapter {
    private boolean upPressed;
    private boolean downPressed;
    private boolean leftPressed;
    private boolean rightPressed;
    private boolean move1Pressed;
    private boolean move2Pressed;
    private boolean move3Pressed;
    private boolean specialPressed;
    private boolean deathPressed;
    private boolean damagePressed;
    private boolean healPressed;
    private boolean glidePressed;
    private boolean switchCharacterPressed;
    private boolean move1Requested;
    private boolean move2Requested;
    private boolean move3Requested;
    private boolean specialRequested;
    private boolean deathRequested;
    private boolean damageRequested;
    private boolean healRequested;
    private boolean switchCharacterRequested;

    @Override
    public void keyPressed(KeyEvent event) {
        setKeyState(event.getKeyCode(), true);
    }

    @Override
    public void keyReleased(KeyEvent event) {
        setKeyState(event.getKeyCode(), false);
    }

    public boolean isUpPressed() {
        return upPressed;
    }

    public boolean isDownPressed() {
        return downPressed;
    }

    public boolean isLeftPressed() {
        return leftPressed;
    }

    public boolean isRightPressed() {
        return rightPressed;
    }

    public boolean isMove1Pressed() {
        return move1Pressed;
    }

    public boolean isMove2Pressed() {
        return move2Pressed;
    }

    public boolean isMove3Pressed() {
        return move3Pressed;
    }

    public boolean isSpecialPressed() {
        return specialPressed;
    }

    public boolean isGlidePressed() {
        return glidePressed;
    }

    public boolean consumeMove1Requested() {
        boolean requested = move1Requested;
        move1Requested = false;
        return requested;
    }

    public boolean consumeMove2Requested() {
        boolean requested = move2Requested;
        move2Requested = false;
        return requested;
    }

    public boolean consumeMove3Requested() {
        boolean requested = move3Requested;
        move3Requested = false;
        return requested;
    }

    public boolean consumeSpecialRequested() {
        boolean requested = specialRequested;
        specialRequested = false;
        return requested;
    }

    public boolean consumeDeathRequested() {
        boolean requested = deathRequested;
        deathRequested = false;
        return requested;
    }

    public boolean consumeDamageRequested() {
        boolean requested = damageRequested;
        damageRequested = false;
        return requested;
    }

    public boolean consumeHealRequested() {
        boolean requested = healRequested;
        healRequested = false;
        return requested;
    }

    public boolean consumeSwitchCharacterRequested() {
        boolean requested = switchCharacterRequested;
        switchCharacterRequested = false;
        return requested;
    }

    private void setKeyState(int keyCode, boolean pressed) {
        switch (keyCode) {
            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                upPressed = pressed;
                break;
            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                downPressed = pressed;
                break;
            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                leftPressed = pressed;
                break;
            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                rightPressed = pressed;
                break;
            case KeyEvent.VK_J:
                if (pressed && !move1Pressed) {
                    move1Requested = true;
                }
                move1Pressed = pressed;
                break;
            case KeyEvent.VK_K:
                if (pressed && !move2Pressed) {
                    move2Requested = true;
                }
                move2Pressed = pressed;
                break;
            case KeyEvent.VK_E:
                if (pressed && !move3Pressed) {
                    move3Requested = true;
                }
                move3Pressed = pressed;
                break;
            case KeyEvent.VK_L:
                if (pressed && !specialPressed) {
                    specialRequested = true;
                }
                specialPressed = pressed;
                break;
            case KeyEvent.VK_Z:
                if (pressed && !deathPressed) {
                    deathRequested = true;
                }
                deathPressed = pressed;
                break;
            case KeyEvent.VK_X:
                if (pressed && !damagePressed) {
                    damageRequested = true;
                }
                damagePressed = pressed;
                break;
            case KeyEvent.VK_C:
                if (pressed && !healPressed) {
                    healRequested = true;
                }
                healPressed = pressed;
                break;
            case KeyEvent.VK_SPACE:
                glidePressed = pressed;
                break;
            case KeyEvent.VK_P:
                if (pressed && !switchCharacterPressed) {
                    switchCharacterRequested = true;
                }
                switchCharacterPressed = pressed;
                break;
            default:
                break;
        }
    }
}