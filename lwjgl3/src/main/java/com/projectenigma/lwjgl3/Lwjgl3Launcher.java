package com.projectenigma.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.projectenigma.ProjectEnigmaGame;

/**
 * Recognizes two manual-testing flags for Phase 1 LAN PvP (see DESIGN.md,
 * "Setup instructions"): {@code --host} and {@code --connect <ip>}. Parsing
 * happens here, in the platform module, because {@code core} must stay
 * platform-agnostic; the parsed args are simply forwarded to {@code
 * ProjectEnigmaGame}, which decides what to do with them. This is also where a
 * future mobile or web launcher would plug in its own equivalent of "how do
 * I get connection info from this platform" without touching core at all.
 *
 * <pre>
 *   ./gradlew lwjgl3:run --args="--host"
 *   ./gradlew lwjgl3:run --args="--connect 127.0.0.1"
 * </pre>
 *
 * Windows: identical, using {@code gradlew.bat} (Command Prompt or
 * PowerShell) -- Gradle's {@code --args} handling is the same on both
 * platforms; no Windows-specific quoting is needed for these two flags.
 */
public final class Lwjgl3Launcher {
    private Lwjgl3Launcher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(ProjectEnigmaGame.DISPLAY_NAME);
        configuration.setWindowedMode(1280, 720);
        configuration.setResizable(true);
        configuration.useVsync(true);
        configuration.setForegroundFPS(60);
        configuration.setIdleFPS(30);
        ProjectEnigmaGame game = new ProjectEnigmaGame(args);
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override public void focusGained() { if (game.getScreen() != null) game.getScreen().resume(); }
            @Override public void focusLost() { if (game.getScreen() != null) game.getScreen().pause(); }
        });
        new Lwjgl3Application(game, configuration);
    }
}
