/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file stores the full lobby state sent by the server. It contains all lobby players and the countdown timer.
 */
package com.engkanto.common.model;

import java.util.ArrayList;
import java.util.List;

public final class LobbySnapshot {
    public List<LobbyPlayerSnapshot> players = new ArrayList<>();
    public int countdownSeconds = -1;

    public LobbySnapshot() {
    }
}
