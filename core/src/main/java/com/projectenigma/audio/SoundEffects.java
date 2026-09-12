package com.projectenigma.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.function.Function;

/** One shared sound bank. Scheduled cues are cancelled when their screen is left. */
public final class SoundEffects implements Disposable {
    private static final float MASTER_VOLUME = .65f;
    private final EnumMap<SoundCue, Sound> sounds = new EnumMap<>(SoundCue.class);
    private final ArrayList<Pending> pending = new ArrayList<>();
    private float hoverCooldown;
    private boolean loaded;
    private boolean disposed;
    private static final class Pending {
        final Object owner;
        final SoundCue cue;
        float remaining;
        Pending(Object owner, SoundCue cue, float delay) { this.owner = owner; this.cue = cue; remaining = delay; }
    }

    public void load() {
        load(cue -> Gdx.audio == null ? null : Gdx.audio.newSound(Gdx.files.internal(cue.path)));
    }

    /** Loader seam lets tests verify playback without a physical audio device. */
    public void load(Function<SoundCue, Sound> loader) {
        if (loaded || disposed) return;
        loaded = true;
        for (SoundCue cue : SoundCue.values()) {
            try {
                Sound sound = loader.apply(cue);
                if (sound != null) sounds.put(cue, sound);
            } catch (RuntimeException exception) { report(cue, exception); }
        }
        if (Gdx.app != null) Gdx.app.log("SoundEffects", "Loaded " + sounds.size() + "/" + SoundCue.values().length + " effects.");
    }

    public void play(SoundCue cue) {
        if (disposed || (cue == SoundCue.HOVER && hoverCooldown > 0)) return;
        Sound sound = sounds.get(cue);
        if (sound == null) return;
        if (cue == SoundCue.HOVER) hoverCooldown = .045f;
        try { sound.play(MASTER_VOLUME * cue.volume); }
        catch (RuntimeException exception) {
            sounds.remove(cue);
            try { sound.dispose(); } catch (RuntimeException ignored) { }
            report(cue, exception);
        }
    }

    public void schedule(Object owner, SoundCue cue, float delay) {
        if (disposed || !sounds.containsKey(cue)) return;
        if (delay <= 0) play(cue);
        else pending.add(new Pending(owner, cue, delay));
    }

    public void update(float delta) {
        if (disposed) return;
        float elapsed = Math.max(0, Math.min(delta, .1f));
        hoverCooldown = Math.max(0, hoverCooldown - elapsed);
        for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
            Pending item = it.next(); item.remaining -= elapsed;
            if (item.remaining <= .00001f) { it.remove(); play(item.cue); }
        }
    }

    public void cancel(Object owner) { pending.removeIf(item -> item.owner == owner); }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true; pending.clear();
        for (Sound sound : sounds.values()) {
            try { sound.dispose(); } catch (RuntimeException ignored) { }
        }
        sounds.clear();
    }

    private static void report(SoundCue cue, RuntimeException exception) {
        if (Gdx.app != null) Gdx.app.error("SoundEffects", "Audio unavailable for " + cue.path, exception);
    }
}
