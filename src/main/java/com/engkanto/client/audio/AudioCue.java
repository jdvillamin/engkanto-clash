/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file lists the music and sound effect cues used by the game. Each cue stores whether it is music, its volume, and its audio file path.
 */
package com.engkanto.client.audio;

public enum AudioCue {
    MENU_MUSIC(true, 0.8f, "/assets/audio/music/main-menu.wav"),
    IN_GAME_MUSIC(true, 0.15f, "/assets/audio/music/in-game.wav"),
    MENU_SELECT(false, 0.8f, "/assets/audio/sfx/menu-select.wav"),
    MENU_CONFIRM(false, 0.8f, "/assets/audio/sfx/menu-confirm.wav"),
    MOVE_START(false, 0.8f, "/assets/audio/sfx/move.wav"),
    JUMP(false, 0.8f, "/assets/audio/sfx/jump.wav"),
    LAND(false, 0.8f, "/assets/audio/sfx/land.wav"),
    ATTACK_1(false, 0.8f, "/assets/audio/sfx/attack-1.wav"),
    ATTACK_2(false, 0.8f, "/assets/audio/sfx/attack-2.wav"),
    ATTACK_3(false, 0.8f, "/assets/audio/sfx/attack-3.wav"),
    SPECIAL(false, 0.8f, "/assets/audio/sfx/special.wav"),
    PLAYER_HURT(false, 0.8f, "/assets/audio/sfx/hurt.wav"),
    PLAYER_HEAL(false, 0.8f, "/assets/audio/sfx/heal.wav"),
    CHARACTER_SWITCH(false, 0.8f, "/assets/audio/sfx/menu-confirm.wav"),
    PLAYER_DEATH(false, 0.8f, "/assets/audio/sfx/death.wav");

    private final boolean music;
    private final float volume;
    private final String[] resourcePaths;

    AudioCue(boolean music, float volume, String... resourcePaths) {
        this.music = music;
        this.volume = volume;
        this.resourcePaths = resourcePaths;
    }

    public boolean isMusic() {
        return music;
    }

    public float getVolume() {
        return volume;
    }

    public String[] getResourcePaths() {
        return resourcePaths.clone();
    }
}
