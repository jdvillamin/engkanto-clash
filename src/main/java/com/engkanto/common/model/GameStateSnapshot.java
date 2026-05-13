package com.engkanto.common.model;

import java.util.ArrayList;
import java.util.List;

public final class GameStateSnapshot {
    public long tick;
    public List<PlayerSnapshot> players = new ArrayList<>();

    public GameStateSnapshot() {
    }

    public GameStateSnapshot(long tick, List<PlayerSnapshot> players) {
        this.tick = tick;
        this.players = players;
    }
}
