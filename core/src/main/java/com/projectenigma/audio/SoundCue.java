package com.projectenigma.audio;

/** All effects are short, original mono PCM WAVs, owned by SoundEffects. */
public enum SoundCue {
    HOVER("hover", .32f), CLICK("click", .42f), DAMAGE("damage", .70f),
    HEAL("heal", .55f), CHEST("chest", .60f), SKILL("skill", .55f),
    ENCOUNTER("encounter", .55f), POWER_UP("power_up", .60f);

    public final String path;
    public final float volume;
    SoundCue(String name, float volume) { path = "assets/audio/" + name + ".wav"; this.volume = volume; }
}
