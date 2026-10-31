package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Slider;
import com.nikohalfkino.rubydung.interfaces.widget.TextInput;
import com.nikohalfkino.rubydung.util.AutosaveSettings;
import com.nikohalfkino.rubydung.util.GameSettings;

public class OptionsScreen extends MenuScreen {

    private final TextInput usernameInput;

    public OptionsScreen(RubyDung game) {
        super(game);

        setKeyRepeat(true);
        setSwallowKeys(true);
        onEscape(this::goBack);

        addTitle("Options");

        Column menu = addColumn(UiStyle.BUTTON_GAP, UiStyle.CONTENT_Y);

        menu.add(new Slider("FOV", UiStyle.ROW_WIDTH, UiStyle.ROW_HEIGHT,
                GameSettings.MIN_FOV, GameSettings.MAX_FOV, GameSettings.getFov(), true)
                .onChange(value -> GameSettings.setFov(value))
                .onCommit(GameSettings::save));

        menu.add(new Slider("Music", UiStyle.ROW_WIDTH, UiStyle.ROW_HEIGHT,
                0.0f, 100.0f, Math.round(GameSettings.getMusicVolume() * 100.0f), true)
                .onChange(value -> GameSettings.setMusicVolume(value / 100.0f))
                .onCommit(GameSettings::save));

        // Whole minutes; 0 turns autosave off.
        menu.add(new Slider("Autosave", UiStyle.ROW_WIDTH, UiStyle.ROW_HEIGHT,
                0.0f, AutosaveSettings.MAX_MINUTES, AutosaveSettings.getIntervalMinutes(), true)
                .setFormatter(value -> value < 0.5f ? "Off" : Math.round(value) + " min")
                .onChange(value -> AutosaveSettings.setIntervalMinutes(Math.round(value)))
                .onCommit(AutosaveSettings::save));

        this.usernameInput = menu.add(new TextInput("Username: ", UiStyle.ROW_WIDTH, UiStyle.ROW_HEIGHT,
                        GameSettings.MAX_USERNAME_LENGTH)
                        .setCommitOnKeys(true)
                        .onCommit(this::commitUsername),
                UiStyle.BUTTON_GAP * 2);
        this.usernameInput.setText(GameSettings.getUsername());

        menu.add(new Button("Controls").onClick(() -> game.openScreen(new ControlsScreen(game))));
        menu.add(new Button("Audio Settings").onClick(() -> game.openScreen(new AudioSettingsScreen(game))));
        menu.add(new Button("Video Settings").onClick(() -> game.openScreen(new VideoSettingsScreen(game))),
                UiStyle.BUTTON_GAP * 2);
        menu.add(new Button("Done").onClick(this::goBack));
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

    private void goBack() {
        if (this.game.hasWorld()) {
            this.game.closeScreen();
        } else {
            this.game.openScreen(new TitleScreen(this.game));
        }
    }
}