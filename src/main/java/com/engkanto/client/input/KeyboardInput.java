package com.engkanto.client.input;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public final class KeyboardInput extends KeyAdapter {
    private boolean upPressed;
    private boolean downPressed;
    private boolean leftPressed;
    private boolean rightPressed;

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
            default:
                break;
        }
    }
}
