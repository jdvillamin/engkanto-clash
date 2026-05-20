/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file controls music and sound effects for the client. It plays WAV clips, loops background music, and prevents duplicate music from stacking.
 */
package com.engkanto.client.audio;

import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

public final class AudioManager {
    private static final AudioManager INSTANCE = new AudioManager();

    private final ExecutorService soundExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });
    private final Map<AudioCue, Clip> loopingClips = new EnumMap<>(AudioCue.class);
    private final Set<String> missingResourcesLogged = new HashSet<>();

    private Clip currentMusic;
    private AudioCue activeMusicCue;
    private long musicRequestId;
    private boolean musicEnabled = true;
    private boolean soundEnabled = true;
    
    public static float musicVolumeMultiplier = 1.0f;
    public static float sfxVolumeMultiplier = 1.0f;

    private AudioManager() {
    }

    public static AudioManager getInstance() {
        return INSTANCE;
    }

    public synchronized void setMusicVolume(float volume) {
        musicVolumeMultiplier = clamp(volume, 0f, 1f);

        if (currentMusic != null && activeMusicCue != null) {
            setVolume(currentMusic,
                    activeMusicCue.getVolume() * musicVolumeMultiplier);
        }
    }

    public synchronized void setSfxVolume(float volume) {
        sfxVolumeMultiplier = clamp(volume, 0f, 1f);
    }


    public synchronized void playMusic(AudioCue cue) {
        if (cue == null || !cue.isMusic() || !musicEnabled) {
            return;
        }
        if (cue == activeMusicCue) {
            return;
        }

        stopMusic();

        String resourcePath = resolveResourcePath(cue);
        if (resourcePath == null) {
            return;
        }

        activeMusicCue = cue;
        long requestId = ++musicRequestId;
        soundExecutor.submit(() -> playMusicResource(cue, resourcePath, requestId));
    }

    public synchronized void stopMusic() {
        musicRequestId++;
        activeMusicCue = null;
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.close();
            currentMusic = null;
        }
    }

    public void playSound(AudioCue cue) {
        playSound(cue, 1.0f);
    }

    public void playSound(AudioCue cue, float volume) {
        if (cue == null || cue.isMusic() || !soundEnabled) {
            return;
        }

        String resourcePath = resolveResourcePath(cue);
        if (resourcePath == null) {
            return;
        }

        soundExecutor.submit(() -> playClip(resourcePath, volume));
    }

    public synchronized void playLoopingSound(AudioCue cue) {
        if (cue == null || cue.isMusic() || !soundEnabled) {
            return;
        }

        Clip existingClip = loopingClips.get(cue);
        if (existingClip != null && existingClip.isRunning()) {
            return;
        }

        String resourcePath = resolveResourcePath(cue);
        if (resourcePath == null) {
            return;
        }

        soundExecutor.submit(() -> playLoopingClip(cue, resourcePath));
    }

    public synchronized void stopLoopingSound(AudioCue cue) {
        Clip clip = loopingClips.remove(cue);
        if (clip != null) {
            clip.stop();
            clip.close();
        }
    }

    public synchronized void setMusicEnabled(boolean enabled) {
        musicEnabled = enabled;
        if (!enabled) {
            stopMusic();
        }
    }

    public synchronized void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
    }

    public void stopAll() {
        stopMusic();
        stopAllLoopingSounds();
    }

    public synchronized void shutdown() {
        stopMusic();
        stopAllLoopingSounds();
        soundExecutor.shutdownNow();
    }

    private void playMusicResource(AudioCue cue, String resourcePath, long requestId) {
        try (AudioInputStream input = openPlayableAudioStream(resourcePath)) {
            Clip clip = AudioSystem.getClip();
            synchronized (this) {
                if (requestId != musicRequestId || cue != activeMusicCue || !musicEnabled) {
                    clip.close();
                    return;
                }
                if (currentMusic != null) {
                    currentMusic.stop();
                    currentMusic.close();
                }
                currentMusic = clip;
            }

            clip.open(input);
            setVolume(clip, cue.getVolume() * musicVolumeMultiplier);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
        } catch (Exception exception) {
            synchronized (this) {
                if (requestId == musicRequestId && cue == activeMusicCue) {
                    activeMusicCue = null;
                    currentMusic = null;
                }
            }
            System.err.println("Failed to play music " + resourcePath + ": " + exception.getMessage());
        }
    }

    private void playClip(String resourcePath, float volume) {
        try (AudioInputStream input = openPlayableAudioStream(resourcePath)) {
            Clip clip = AudioSystem.getClip();
            clip.open(input);
            setVolume(clip, volume * sfxVolumeMultiplier);
            clip.addLineListener(event -> closeClipWhenStopped(clip, event));
            clip.start();
        } catch (Exception exception) {
            System.err.println("Failed to play sound " + resourcePath + ": " + exception.getMessage());
        }
    }

    private void playLoopingClip(AudioCue cue, String resourcePath) {
        try (AudioInputStream input = openPlayableAudioStream(resourcePath)) {
            Clip clip = AudioSystem.getClip();
            clip.open(input);

            synchronized (this) {
                if (!soundEnabled || loopingClips.containsKey(cue)) {
                    clip.close();
                    return;
                }
                loopingClips.put(cue, clip);
            }

            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
        } catch (Exception exception) {
            synchronized (this) {
                loopingClips.remove(cue);
            }
            System.err.println("Failed to play looping sound " + resourcePath + ": " + exception.getMessage());
        }
    }

    private AudioInputStream openPlayableAudioStream(String resourcePath) throws Exception {
        AudioInputStream sourceStream = AudioSystem.getAudioInputStream(requireResourceUrl(resourcePath));
        AudioFormat sourceFormat = sourceStream.getFormat();

        if (AudioFormat.Encoding.PCM_SIGNED.equals(sourceFormat.getEncoding())
                && sourceFormat.getSampleSizeInBits() == 16) {
            return sourceStream;
        }

        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                16,
                sourceFormat.getChannels(),
                sourceFormat.getChannels() * 2,
                sourceFormat.getSampleRate(),
                false
        );
        return AudioSystem.getAudioInputStream(targetFormat, sourceStream);
    }

    private URL requireResourceUrl(String resourcePath) throws IOException {
        URL resourceUrl = getClass().getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return resourceUrl;
    }

    private String resolveResourcePath(AudioCue cue) {
        for (String resourcePath : cue.getResourcePaths()) {
            if (getClass().getResource(resourcePath) != null) {
                return resourcePath;
            }
            logMissingResource(resourcePath);
        }
        return null;
    }

    private void logMissingResource(String resourcePath) {
        synchronized (missingResourcesLogged) {
            if (!missingResourcesLogged.add(resourcePath)) {
                return;
            }
        }
        System.err.println("Audio resource not found: " + resourcePath);
    }

    private void setVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float safeVolume = clamp(volume, 0.0001f, 1.0f);
        float gain = (float) (Math.log10(safeVolume) * 20.0);
        gainControl.setValue(clamp(gain, gainControl.getMinimum(), gainControl.getMaximum()));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void closeClipWhenStopped(Clip clip, LineEvent event) {
        if (event.getType() == LineEvent.Type.STOP) {
            clip.close();
        }
    }

    private synchronized void stopAllLoopingSounds() {
        for (Clip clip : loopingClips.values()) {
            clip.stop();
            clip.close();
        }
        loopingClips.clear();
    }
}
