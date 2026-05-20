package com.engkanto.common.model;

import java.util.ArrayList;
import java.util.List;

public final class GameStateSnapshot {
    public long tick;
    public List<PlayerSnapshot> players = new ArrayList<>();
    public double secondsRemaining;
    public boolean gameOver;

    public GameStateSnapshot() {
    }

    public GameStateSnapshot(long tick, List<PlayerSnapshot> players, double secondsRemaining, boolean gameOver) {
        this.tick = tick;
        this.players = players;
        this.secondsRemaining = secondsRemaining;
        this.gameOver = gameOver;
    }
}
