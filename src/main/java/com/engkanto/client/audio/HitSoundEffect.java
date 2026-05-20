/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file provides a small pooled hit sound effect helper. It lets repeated hits play quickly without waiting for one clip to finish.
 */
package com.engkanto.client.audio;

import java.util.Random;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public final class HitSoundEffect {
    private static final int SAMPLE_RATE = 22050;
    private static final double DURATION_SECONDS = 0.07;
    private static final float VOLUME = 0.35f;
    private static final int POOL_SIZE = 4;

    private static HitSoundEffect instance;

    private final byte[] soundData;
    private final AudioFormat format;
    private final Clip[] clips;
    private int nextClip;

    private HitSoundEffect() {
        format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        int numSamples = (int) (SAMPLE_RATE * DURATION_SECONDS);
        soundData = new byte[numSamples * 2];
        Random random = new Random(42);

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / numSamples;
            double envelope = Math.exp(-10.0 * t);
            double noise = random.nextDouble() * 2.0 - 1.0;
            double thump = Math.sin(2.0 * Math.PI * 90.0 * i / SAMPLE_RATE);
            double sample = (noise * 0.55 + thump * 0.45) * envelope * VOLUME;
            short value = (short) (sample * Short.MAX_VALUE);
            soundData[i * 2] = (byte) (value & 0xFF);
            soundData[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }

        clips = new Clip[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                clips[i] = AudioSystem.getClip();
                clips[i].open(format, soundData, 0, soundData.length);
            } catch (Exception exception) {
                clips[i] = null;
            }
        }
    }

    public static synchronized HitSoundEffect getInstance() {
        if (instance == null) {
            instance = new HitSoundEffect();
        }
        return instance;
    }

    public void play() {
        Clip clip = clips[nextClip];
        nextClip = (nextClip + 1) % POOL_SIZE;
        if (clip == null) {
            return;
        }
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }
}
