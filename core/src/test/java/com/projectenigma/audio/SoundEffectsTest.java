package com.projectenigma.audio;

import com.badlogic.gdx.audio.Sound;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SoundEffectsTest {
    private final List<SoundCue> played = new ArrayList<>();
    private final List<SoundCue> disposed = new ArrayList<>();
    private Sound sound(SoundCue cue, boolean failPlay) {
        return (Sound)Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class<?>[]{Sound.class}, (o, m, args) -> {
            if (m.getName().equals("play")) {
                assertTrue((float)args[0] > 0 && (float)args[0] < 1);
                if (failPlay) throw new IllegalStateException("audio device lost");
                played.add(cue); return 1L;
            }
            if (m.getName().equals("dispose")) disposed.add(cue);
            return m.getReturnType() == long.class ? 0L : null;
        });
    }
    @Test void loadsOnceAndDisposesEverySoundExactlyOnce() {
        SoundEffects bank = new SoundEffects();
        bank.load(cue -> sound(cue, false)); bank.load(cue -> { fail("Loaded twice"); return null; });
        for (SoundCue cue : SoundCue.values()) bank.play(cue);
        assertEquals(8, played.size());
        bank.dispose(); bank.dispose(); bank.play(SoundCue.CLICK);
        assertEquals(8, disposed.size()); assertEquals(8, played.size());
    }
    @Test void timingAndCancellationPreventLateOrDuplicateHits() {
        SoundEffects bank = new SoundEffects(); bank.load(cue -> sound(cue, false));
        Object screen = new Object(), other = new Object();
        bank.schedule(screen, SoundCue.DAMAGE, .22f); bank.schedule(other, SoundCue.HEAL, .12f);
        bank.update(.1f); assertTrue(played.isEmpty());
        bank.update(.1f); assertEquals(List.of(SoundCue.HEAL), played);
        bank.cancel(screen); bank.update(.1f); assertEquals(List.of(SoundCue.HEAL), played);
        bank.schedule(screen, SoundCue.DAMAGE, .1f); bank.update(.1f); bank.update(.1f);
        assertEquals(List.of(SoundCue.HEAL, SoundCue.DAMAGE), played);
    }
    @Test void hoverIsQuietlyRateLimitedButClicksAreNotSuppressed() {
        SoundEffects bank = new SoundEffects(); bank.load(cue -> sound(cue, false));
        bank.play(SoundCue.HOVER); bank.play(SoundCue.HOVER); bank.play(SoundCue.CLICK);
        assertEquals(List.of(SoundCue.HOVER, SoundCue.CLICK), played);
        bank.update(.05f); bank.play(SoundCue.HOVER); assertEquals(3, played.size());
    }
    @Test void missingFileOrLostAudioDeviceDoesNotBreakOtherSounds() {
        SoundEffects bank = new SoundEffects();
        bank.load(cue -> { if (cue == SoundCue.CHEST) throw new IllegalArgumentException("missing file"); return sound(cue, cue == SoundCue.HEAL); });
        assertDoesNotThrow(() -> { bank.play(SoundCue.CHEST); bank.play(SoundCue.HEAL); bank.play(SoundCue.HEAL); bank.play(SoundCue.CLICK); });
        assertEquals(List.of(SoundCue.CLICK), played); assertEquals(List.of(SoundCue.HEAL), disposed);
        bank.dispose(); assertEquals(7, disposed.size());
    }
}
