package com.engkanto.common.model;

import java.util.ArrayList;
import java.util.List;

public final class LobbySnapshot {
    public List<LobbyPlayerSnapshot> players = new ArrayList<>();
    public int countdownSeconds = -1;

    public LobbySnapshot() {
    }
}
