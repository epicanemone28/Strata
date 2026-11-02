package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.Label;
import com.nikohalfkino.rubydung.interfaces.widget.Panel;
import com.nikohalfkino.rubydung.interfaces.widget.Row;
import com.nikohalfkino.rubydung.interfaces.widget.Slider;
import com.nikohalfkino.rubydung.interfaces.widget.TextInput;
import com.nikohalfkino.rubydung.util.AutosaveSettings;
import com.nikohalfkino.rubydung.util.GameSettings;

public class OptionsScreen extends MenuScreen {

    private static final float PANEL_WIDTH     = UiStyle.PANEL_WIDTH;
    private static final float FIELD_HEIGHT    = UiStyle.ROW_HEIGHT;
    private static final float BUTTON_HEIGHT   = UiStyle.BUTTON_HEIGHT;
    private static final float PANEL_PADDING   = 20.0f;

    /** Gap between items inside a section. */
    private static final float ITEM_GAP        = 8.0f;
    /** Gap from a section header label to its first control. */
    private static final float HEADER_GAP      = 6.0f;
    /** Gap from the last item in a section to the next section's header. */
    private static final float SECTION_GAP     = 16.0f;

    /** Three "more" buttons share the panel width. */
    private static final float MORE_BUTTON_GAP   = 9.0f;
    private static final float MORE_BUTTON_WIDTH  = (PANEL_WIDTH - 2 * MORE_BUTTON_GAP) / 3.0f;

    /** How far below the window top the panel's top edge sits (gives room for the title). */
    private static final float PANEL_TOP = 90.0f;

    private final TextInput usernameInput;

    public OptionsScreen(RubyDung game) {
        super(game);

        setKeyRepeat(true);
        setSwallowKeys(true);
        onEscape(this::goBack);

        addTitle("Options");

        Panel panel = add(new Panel(ITEM_GAP), Anchor.TOP_CENTER, 0, PANEL_TOP);
        panel.setPadding(PANEL_PADDING);
        panel.setShadowAlpha(1.0f);
        panel.setCrossAlign(0.0f);

        // — Gameplay ——————————————————————————————————————————
        panel.add(new Label("Gameplay").setColor(UiStyle.TEXT_DIM), HEADER_GAP);

        panel.add(new Slider("FOV", PANEL_WIDTH, FIELD_HEIGHT,
                GameSettings.MIN_FOV, GameSettings.MAX_FOV, GameSettings.getFov(), true)
                .onChange(GameSettings::setFov)
                .onCommit(GameSettings::save));

        panel.add(new Slider("Autosave", PANEL_WIDTH, FIELD_HEIGHT,
                0.0f, AutosaveSettings.MAX_MINUTES, AutosaveSettings.getIntervalMinutes(), true)
                .setFormatter(v -> v < 0.5f ? "Off" : Math.round(v) + " min")
                .onChange(v -> AutosaveSettings.setIntervalMinutes(Math.round(v)))
                .onCommit(AutosaveSettings::save), SECTION_GAP);

        // — Audio —————————————————————————————————————————————
        panel.add(new Label("Audio").setColor(UiStyle.TEXT_DIM), HEADER_GAP);

        panel.add(new Slider("Music", PANEL_WIDTH, FIELD_HEIGHT,
                0.0f, 100.0f, Math.round(GameSettings.getMusicVolume() * 100.0f), true)
                .onChange(v -> GameSettings.setMusicVolume(v / 100.0f))
                .onCommit(GameSettings::save), SECTION_GAP);

        // — Account ———————————————————————————————————————————
        panel.add(new Label("Account").setColor(UiStyle.TEXT_DIM), HEADER_GAP);

        this.usernameInput = panel.add(
                new TextInput("Username: ", PANEL_WIDTH, FIELD_HEIGHT, GameSettings.MAX_USERNAME_LENGTH)
                        .setCommitOnKeys(true)
                        .onCommit(this::commitUsername),
                SECTION_GAP);
        this.usernameInput.setText(GameSettings.getUsername());

        // — More Settings —————————————————————————————————————
        panel.add(new Label("More Settings").setColor(UiStyle.TEXT_DIM), HEADER_GAP);

        Row moreRow = panel.add(new Row(MORE_BUTTON_GAP), SECTION_GAP);
        moreRow.add(new Button("Controls",      MORE_BUTTON_WIDTH, BUTTON_HEIGHT, true)
                .onClick(() -> game.openScreen(new ControlsScreen(game))));
        moreRow.add(new Button("Voice",         MORE_BUTTON_WIDTH, BUTTON_HEIGHT, true)
                .onClick(() -> game.openScreen(new VoiceSettingsScreen(game))));
        moreRow.add(new Button("Video",         MORE_BUTTON_WIDTH, BUTTON_HEIGHT, true)
                .onClick(() -> game.openScreen(new VideoSettingsScreen(game))));

        // — Done ——————————————————————————————————————————————
        panel.add(new Button("Done", PANEL_WIDTH, BUTTON_HEIGHT, true)
                .onClick(this::goBack));
    }

    /** Applies the typed name (cleaned up by GameSettings) and saves it if it changed. */
    private void commitUsername() {
        String before = GameSettings.getUsername();

        GameSettings.setUsername(this.usernameInput.getText());
        this.usernameInput.setText(GameSettings.getUsername());

        if (!before.equals(GameSettings.getUsername())) {
            GameSettings.save();
        }
    }

    /**
     * Returns to the screen that launched Options:
     * - in-game → PauseScreen (fixes the bug where Done would drop you straight into the world)
     * - at the title → TitleScreen
     */
    private void goBack() {
        if (this.game.hasWorld()) {
            this.game.openScreen(new PauseScreen(this.game));
        } else {
            this.game.openScreen(new TitleScreen(this.game));
        }
    }
}