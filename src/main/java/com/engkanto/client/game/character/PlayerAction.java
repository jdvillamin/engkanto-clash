package com.engkanto.client.game.character;

public enum PlayerAction {
    IDLE(0, 1, false, new double[] {0.14}),
    WALK(0, 4, true, new double[] {0.14, 0.14, 0.14, 0.14}),
    JUMP(1, 4, false, new double[] {0.12, 0.12, 0.12, 0.12}),
    MOVE_1(2, 4, false, new double[] {0.12, 0.12, 0.12, 0.12}),
    MOVE_2(3, 4, false, new double[] {0.12, 0.12, 0.12, 0.12}),
    MOVE_3(4, 4, false, new double[] {0.10, 0.10, 0.10, 0.10}),
    SPECIAL(5, 4, false, new double[] {0.18, 0.18, 0.55, 0.18}),
    DEATH(6, 4, false, new double[] {0.16, 0.16, 0.16, 0.16});

    private final int row;
    private final int frameCount;
    private final boolean loops;
    private final double[] frameDurations;

    PlayerAction(int row, int frameCount, boolean loops, double[] frameDurations) {
        this.row = row;
        this.frameCount = frameCount;
        this.loops = loops;
        this.frameDurations = frameDurations;
    }

    public int getRow() {
        return row;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public boolean loops() {
        return loops;
    }

    public double getFrameDuration(int frameIndex) {
        return frameDurations[Math.min(frameIndex, frameDurations.length - 1)];
    }
}
