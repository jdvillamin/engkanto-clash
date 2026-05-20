/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file stores lobby information for one player. It includes the player id, selected character, and ready status.
 */
package com.engkanto.common.model;

public final class LobbyPlayerSnapshot {
    public int id;
    public int characterIndex;
    public String characterName;
    public boolean ready;

    public LobbyPlayerSnapshot() {
    }
}
