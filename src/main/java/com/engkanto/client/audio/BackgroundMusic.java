package com.engkanto.client.audio;

import java.io.BufferedInputStream;
import java.io.InputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

public final class BackgroundMusic {
    private static final String MUSIC_PATH = "/assets/audio/background.mp3";

    private volatile boolean playing;
    private volatile SourceDataLine currentLine;

    public void start() {
        if (playing) {
            return;
        }
        playing = true;
        Thread musicThread = new Thread(this::playLoop, "engkanto-music");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public void stop() {
        playing = false;
        SourceDataLine line = currentLine;
        if (line != null) {
            line.close();
        }
    }

    private void playLoop() {
        while (playing) {
            try {
                InputStream stream = getClass().getResourceAsStream(MUSIC_PATH);
                if (stream == null) {
                    System.err.println("Background music not found: " + MUSIC_PATH);
                    return;
                }
                playMp3(new BufferedInputStream(stream));
            } catch (Exception exception) {
                if (playing) {
                    System.err.println("Background music error: " + exception.getMessage());
                }
                return;
            }
        }
    }

    private void playMp3(InputStream stream) throws Exception {
        Bitstream bitstream = new Bitstream(stream);
        Decoder decoder = new Decoder();

        Header header = bitstream.readFrame();
        if (header == null) {
            return;
        }

        SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
        AudioFormat format = new AudioFormat(
                decoder.getOutputFrequency(),
                16,
                decoder.getOutputChannels(),
                true,
                false
        );

        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        currentLine = line;
        line.open(format);
        line.start();

        writeFrame(line, output);
        bitstream.closeFrame();

        while (playing) {
            header = bitstream.readFrame();
            if (header == null) {
                break;
            }
            output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            writeFrame(line, output);
            bitstream.closeFrame();
        }

        line.drain();
        line.close();
        currentLine = null;
        bitstream.close();
    }

    private void writeFrame(SourceDataLine line, SampleBuffer output) {
        short[] samples = output.getBuffer();
        int length = output.getBufferLength();
        byte[] buffer = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            buffer[i * 2] = (byte) (samples[i] & 0xFF);
            buffer[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        line.write(buffer, 0, buffer.length);
    }
}
