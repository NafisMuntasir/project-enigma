package com.projectenigma.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.projectenigma.ProjectEnigmaGame;
import com.projectenigma.Palette;
import com.projectenigma.UiRenderer;

import java.io.IOException;

/**
 * "Main Menu -&gt; Multiplayer -&gt; Host or Join" per the Phase 1 UI flow.
 * Once hosting starts or a connection is initiated, this screen just shows
 * status text; {@code ProjectEnigmaGame}'s PvP listeners handle the actual
 * transition onward to {@code ClassSelectScreen} once a connection lands
 * (see ProjectEnigmaGame.hostPvPMatch/joinPvPMatch) -- this screen does not
 * need to poll anything itself, because {@code PvPServer}/{@code PvPClient}
 * schedule their callbacks via {@code Gdx.app.postRunnable}, which libGDX
 * runs before the next frame regardless of which screen is active.
 */
public final class MultiplayerMenuScreen extends AbstractGameScreen {
    private enum Mode { MENU, HOSTING_WAIT, JOIN_INPUT, JOIN_CONNECTING }

    // Index 2's label is dynamic (see labelFor) so it can show the current
    // Race Mode ON/OFF state; the array below only fixes the count and the
    // fallback text for the other three entries.
    private static final String[] OPTIONS = {"Host", "Join", "Race Mode", "Back"};
    private static final int RACE_TOGGLE_INDEX = 2;

    private final OrthographicCamera camera;
    private final FitViewport viewport;
    private Mode mode = Mode.MENU;
    private int selected;
    private final StringBuilder ipInput = new StringBuilder("127.0.0.1");
    private String notice = "";
    private boolean addressFocused = true;
    private boolean selectAll;
    private int caret = 9;

    /**
     * When ON, Host/Join start a "Race to PvP" match instead of classic PvP:
     * both players explore their own independent single-player dungeon for
     * a fixed duration, then are transitioned into a normal PvP fight using
     * the heroes they grew (see {@code ProjectEnigmaGame}'s "Race-to-PvP
     * lifecycle" section and DESIGN.md). Toggling this here, before
     * Host/Join is pressed, is simpler than duplicating a whole second set
     * of Host/Join buttons for a mode that only changes what happens after
     * the connection is made.
     */
    private boolean raceModeSelected;

    /** Exploration lengths offered by the "-"/"+" duration selector, shown only while Race Mode is ON. Only the host's choice matters -- it's sent to the guest in RaceStartPacket. */
    private static final int[] RACE_DURATION_PRESETS_SECONDS = {30, 60, 120, 180, 300, 420, 600};
    private int raceDurationIndex = indexOfDefaultDuration();
    private static int indexOfDefaultDuration() {
        for (int i = 0; i < RACE_DURATION_PRESETS_SECONDS.length; i++) {
            if (RACE_DURATION_PRESETS_SECONDS[i] == ProjectEnigmaGame.RACE_DEFAULT_DURATION_SECONDS) return i;
        }
        return 3;
    }

    public MultiplayerMenuScreen(ProjectEnigmaGame game) {
        super(game);
        camera = new OrthographicCamera();
        viewport = new FitViewport(UiRenderer.WIDTH, UiRenderer.HEIGHT, camera);
        useInput(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return switch (mode) {
                    case MENU -> handleMenuInput(keycode);
                    case JOIN_INPUT -> handleJoinInput(keycode);
                    case HOSTING_WAIT, JOIN_CONNECTING -> handleWaitingInput(keycode);
                };
            }
            @Override public boolean keyTyped(char character) {
                if (mode != Mode.JOIN_INPUT || !addressFocused) return false;
                if ((character >= '0' && character <= '9') || character == '.') {
                    if (selectAll) { ipInput.setLength(0); caret = 0; selectAll = false; }
                    if (ipInput.length() < 64) ipInput.insert(caret++, character);
                }
                return true;
            }
        });
        useMouse(viewport);
        for (int i = 0; i < OPTIONS.length; i++) {
            final int index = i;
            mouseUi.add(() -> labelFor(index), 465, 400 - i * 66, 350, 48, () -> { selected = index; activateSelection(); })
                    .when(() -> mode == Mode.MENU).large().hover(() -> selected = index).selected(() -> selected == index);
        }
        // Duration selector: only meaningful (and only shown) while Race Mode is ON.
        // Placed in the panel's own left/right margins (the panel spans x 365-915;
        // the main 350-wide option buttons already use the full width minus 100px
        // on each side) so both buttons stay inside the visible panel instead of
        // floating in the dimmed backdrop outside it.
        mouseUi.add("-", 375, 400 - RACE_TOGGLE_INDEX * 66, 75, 48, () -> adjustRaceDuration(-1))
                .when(() -> mode == Mode.MENU && raceModeSelected);
        mouseUi.add("+", 830, 400 - RACE_TOGGLE_INDEX * 66, 75, 48, () -> adjustRaceDuration(1))
                .when(() -> mode == Mode.MENU && raceModeSelected);
        mouseUi.add(this::addressLabel, 415, 310, 450, 56, () -> addressFocused = true)
                .when(() -> mode == Mode.JOIN_INPUT).selected(() -> addressFocused);
        mouseUi.add("Paste", 435, 235, 120, 48, this::pasteAddress).when(() -> mode == Mode.JOIN_INPUT);
        mouseUi.add("Connect", 565, 235, 140, 48, this::connectAddress).when(() -> mode == Mode.JOIN_INPUT);
        mouseUi.add("Back", 715, 235, 130, 48, () -> mode = Mode.MENU).when(() -> mode == Mode.JOIN_INPUT);
        mouseUi.add("Cancel", 540, 210, 200, 48, this::cancelWaiting)
                .when(() -> mode == Mode.HOSTING_WAIT || mode == Mode.JOIN_CONNECTING);
    }

    private boolean handleMenuInput(int keycode) {
        if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
            selected = Math.floorMod(selected - 1, OPTIONS.length);
            return true;
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
            selected = (selected + 1) % OPTIONS.length;
            return true;
        }
        if (selected == RACE_TOGGLE_INDEX && (keycode == Input.Keys.LEFT || keycode == Input.Keys.A)) {
            adjustRaceDuration(-1);
            return true;
        }
        if (selected == RACE_TOGGLE_INDEX && (keycode == Input.Keys.RIGHT || keycode == Input.Keys.D)) {
            adjustRaceDuration(1);
            return true;
        }
        if (keycode == Input.Keys.ESCAPE) {
            game.showMenu();
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            activateSelection();
            return true;
        }
        return false;
    }

    private void adjustRaceDuration(int direction) {
        raceDurationIndex = Math.floorMod(raceDurationIndex + direction, RACE_DURATION_PRESETS_SECONDS.length);
    }

    private int selectedRaceDurationSeconds() {
        return RACE_DURATION_PRESETS_SECONDS[raceDurationIndex];
    }

    /** Used by Lwjgl3Launcher's {@code --host} command-line flag (see DESIGN.md, "Setup instructions"). */
    public static MultiplayerMenuScreen autoHost(ProjectEnigmaGame game) {
        MultiplayerMenuScreen screen = new MultiplayerMenuScreen(game);
        screen.startHosting();
        return screen;
    }

    /** Used by Lwjgl3Launcher's {@code --connect <ip>} command-line flag. */
    public static MultiplayerMenuScreen autoJoin(ProjectEnigmaGame game, String hostAddress) {
        MultiplayerMenuScreen screen = new MultiplayerMenuScreen(game);
        screen.ipInput.setLength(0);
        screen.ipInput.append(hostAddress);
        game.joinPvPMatch(hostAddress);
        screen.mode = Mode.JOIN_CONNECTING;
        return screen;
    }

    private void startHosting() {
        try {
            if (raceModeSelected) {
                game.hostRaceMatch(selectedRaceDurationSeconds());
            } else {
                game.hostPvPMatch();
            }
            mode = Mode.HOSTING_WAIT;
            notice = "";
        } catch (IOException exception) {
            notice = "Could not start hosting: " + exception.getMessage();
        }
    }

    private void activateSelection() {
        switch (selected) {
            case 0 -> startHosting();
            case 1 -> { mode = Mode.JOIN_INPUT; notice = ""; addressFocused = true; caret = ipInput.length(); selectAll = true; }
            case RACE_TOGGLE_INDEX -> raceModeSelected = !raceModeSelected;
            case 3 -> game.showMenu();
            default -> throw new IllegalStateException("Unknown menu item");
        }
    }

    private String labelFor(int index) {
        if (index == RACE_TOGGLE_INDEX) {
            return raceModeSelected
                    ? "Race: ON (" + formatMinutesSeconds(selectedRaceDurationSeconds()) + ")"
                    : "Race Mode: OFF";
        }
        return OPTIONS[index];
    }

    private String addressLabel() {
        if (!addressFocused) return ipInput.toString();
        if (selectAll) return "[ " + ipInput + " ]";
        return ipInput.substring(0, caret) + "|" + ipInput.substring(caret);
    }

    private void pasteAddress() {
        String text = Gdx.app.getClipboard().getContents();
        if (text == null) return;
        text = text.trim();
        if (text.length() > 64 || !text.matches("[0-9.]*")) { notice = "Paste an IPv4 address, e.g. 192.168.1.10."; return; }
        ipInput.setLength(0); ipInput.append(text); caret = ipInput.length(); selectAll = false; addressFocused = true;
    }

    private void connectAddress() {
        String address = ipInput.toString();
        String[] parts = address.split("\\.", -1);
        boolean valid = parts.length == 4;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.matches("[0-9]+")) { valid = false; break; }
            if (Integer.parseInt(part) > 255) valid = false;
        }
        if (!valid) { notice = "Enter a valid IPv4 address, e.g. 192.168.1.10."; return; }
        if (raceModeSelected) {
            game.joinRaceMatch(address);
        } else {
            game.joinPvPMatch(address);
        }
        mode = Mode.JOIN_CONNECTING; notice = "";
    }

    private boolean handleJoinInput(int keycode) {
        if (keycode == Input.Keys.ESCAPE) { mode = Mode.MENU; return true; }
        if (keycode == Input.Keys.ENTER) { connectAddress(); return true; }
        if (!addressFocused) return false;
        boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (control && keycode == Input.Keys.V) { pasteAddress(); return true; }
        if (control && keycode == Input.Keys.A) { selectAll = true; return true; }
        if (keycode == Input.Keys.BACKSPACE || keycode == Input.Keys.FORWARD_DEL) {
            if (selectAll) { ipInput.setLength(0); caret = 0; selectAll = false; }
            else if (keycode == Input.Keys.BACKSPACE && caret > 0) ipInput.deleteCharAt(--caret);
            else if (keycode == Input.Keys.FORWARD_DEL && caret < ipInput.length()) ipInput.deleteCharAt(caret);
        } else if (keycode == Input.Keys.LEFT) { caret = Math.max(0, caret - 1); selectAll = false; }
        else if (keycode == Input.Keys.RIGHT) { caret = Math.min(ipInput.length(), caret + 1); selectAll = false; }
        else if (keycode == Input.Keys.HOME) { caret = 0; selectAll = false; }
        else if (keycode == Input.Keys.END) { caret = ipInput.length(); selectAll = false; }
        return true;
    }

    private void cancelWaiting() {
        if (mode == Mode.JOIN_CONNECTING && game.pvpClient() != null) game.pvpClient().cancelPendingConnection();
        game.leavePvPMatch();
    }
    private boolean handleWaitingInput(int keycode) {
        if (keycode == Input.Keys.ESCAPE) { cancelWaiting(); return true; }
        return false;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Palette.VOID);
        viewport.apply();
        camera.update();

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().setColor(1f, 1f, 1f, 1f);
        game.batch().begin();
        game.batch().draw(game.assets().menuBackground(), 0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        game.batch().end();

        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(camera.combined);
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.04f, 0.07f, 0.10f, 0.34f);
        shapes.rect(0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        UiRenderer.panel(shapes, 365f, 130f, 550f, 480f, Palette.PANEL);
        shapes.setColor(Palette.ACCENT);
        shapes.rect(365f, 600f, 550f, 10f);
        shapes.setColor(Palette.BLUE);
        shapes.rect(365f, 130f, 7f, 470f);
        shapes.end();
        com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), "MULTIPLAYER", 640f, 580f, Palette.TEXT);
        switch (mode) {
            case MENU -> {
                String modeExplanation = raceModeSelected
                        ? "Race Mode: explore solo for " + formatMinutesSeconds(selectedRaceDurationSeconds())
                          + ", then fight with the hero you grew."
                        : "Classic PvP: pick a class and fight immediately.";
                UiRenderer.centeredText(game.batch(), game.font(), modeExplanation, 640f, 260f, Palette.BLUE_LIGHT);
                String hint = "W/S: select    Enter: confirm    Esc: back";
                if (raceModeSelected) {
                    hint += "    (Left/Right or -/+: change duration)";
                }
                UiRenderer.centeredText(game.batch(), game.font(), hint,
                        640f, 220f, Palette.MUTED);
            }
            case JOIN_INPUT -> {
                UiRenderer.centeredText(game.batch(), game.mediumFont(), "Host IP address:", 640f, 420f, Palette.TEXT);
                UiRenderer.centeredText(game.batch(), game.titleFont(), "", 640f, 350f, Palette.ACCENT);
                UiRenderer.centeredText(game.batch(), game.font(),
                        "Click field to edit | Ctrl+A: select all | Ctrl+V: paste",
                        640f, 190f, Palette.MUTED);
            }
            case HOSTING_WAIT -> {
                UiRenderer.centeredText(game.batch(), game.mediumFont(),
                        "Hosting on port " + ProjectEnigmaGame.PVP_DEFAULT_PORT, 640f, 420f, Palette.TEXT);
                UiRenderer.centeredText(game.batch(), game.font(),
                        "Share your LAN IP address with the other player.", 640f, 385f, Palette.MUTED);
                UiRenderer.centeredText(game.batch(), game.mediumFont(), "Waiting for an opponent...", 640f, 330f, Palette.ACCENT);
                if (raceModeSelected) {
                    UiRenderer.centeredText(game.batch(), game.font(),
                            "Race Mode: " + formatMinutesSeconds(selectedRaceDurationSeconds())
                                    + " to explore once both operatives are picked.", 640f, 295f, Palette.BLUE_LIGHT);
                }
                UiRenderer.centeredText(game.batch(), game.font(), "Esc: cancel", 640f, 185f, Palette.MUTED);
            }
            case JOIN_CONNECTING -> {
                UiRenderer.centeredText(game.batch(), game.mediumFont(), "Connecting to " + ipInput + "...", 640f, 400f, Palette.TEXT);
                UiRenderer.centeredText(game.batch(), game.font(), "Retrying automatically every 3 seconds.", 640f, 360f, Palette.MUTED);
                if (raceModeSelected) {
                    UiRenderer.centeredText(game.batch(), game.font(), "Race Mode selected.", 640f, 325f, Palette.BLUE_LIGHT);
                }
                UiRenderer.centeredText(game.batch(), game.font(), "Esc: cancel", 640f, 185f, Palette.MUTED);
            }
        }
        if (!notice.isEmpty()) {
            UiRenderer.centeredText(game.batch(), game.font(), notice, 640f, 150f, Palette.DANGER);
        }
        game.batch().end();
        drawMouse();
    }

    private static String formatMinutesSeconds(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
