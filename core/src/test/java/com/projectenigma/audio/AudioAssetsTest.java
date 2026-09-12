package com.projectenigma.audio;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AudioAssetsTest {
    @Test void allEightEffectsAreShortNonClippingPcmWavsWithSilentEndpoints() throws Exception {
        for (SoundCue cue : SoundCue.values()) {
            try (var stream = AudioSystem.getAudioInputStream(Path.of("..").resolve(cue.path).toFile())) {
                AudioFormat format = stream.getFormat();
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
                assertEquals(44100, format.getSampleRate()); assertEquals(1, format.getChannels());
                assertEquals(16, format.getSampleSizeInBits());
                byte[] pcm = stream.readAllBytes();
                double duration = pcm.length / 2.0 / format.getSampleRate();
                assertTrue(duration >= .04 && duration <= 1, cue.name());
                int peak = 0;
                for (int i = 0; i < pcm.length; i += 2) {
                    short sample = (short)((pcm[i] & 255) | (pcm[i + 1] << 8));
                    peak = Math.max(peak, Math.abs((int)sample));
                }
                assertTrue(peak > 1000 && peak < 30000, cue.name());
                assertEquals(0, pcm[0]); assertEquals(0, pcm[1]);
                assertEquals(0, pcm[pcm.length - 1]); assertEquals(0, pcm[pcm.length - 2]);
            }
        }
    }
}
