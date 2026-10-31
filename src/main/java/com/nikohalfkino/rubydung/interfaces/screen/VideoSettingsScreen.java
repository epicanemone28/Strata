package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Toggle;
import com.nikohalfkino.rubydung.util.GameSettings;
import org.lwjgl.opengl.Display;

public class VideoSettingsScreen extends MenuScreen {

    private static final float ROW_SPACING = 14.0f;

    private Toggle vsyncToggle;

    public VideoSettingsScreen(RubyDung game) {
        super(game);

        setSwallowKeys(true);
        onEscape(this::goBack);

        addTitle("Video Settings");

        Column menu = addColumn(ROW_SPACING, UiStyle.CONTENT_Y);

        // Greyed out and unclickable while VSync is on, since VSync already caps the frame rate.
        Button fpsCapButton = menu.add(new Button(VideoSettingsScreen::fpsCapLabel,
                UiStyle.BUTTON_WIDTH, UiStyle.BUTTON_HEIGHT, true)
                .onClick(() -> {
                    GameSettings.cycleFpsCap();
                    GameSettings.save();
                }));
        fpsCapButton.setEnabledWhen(() -> !this.vsyncToggle.getValue());

        this.vsyncToggle = menu.add(new Toggle("VSync", GameSettings.isVsyncEnabled(), Toggle.LabelSide.LEFT)
                .setRowWidth(UiStyle.BUTTON_WIDTH)
                .onChange(on -> {
                    GameSettings.setVsyncEnabled(on);
                    Display.setVSyncEnabled(on);
                    GameSettings.save();
                }));

        menu.add(new Toggle("Show FPS", GameSettings.isShowFpsInHud(), Toggle.LabelSide.LEFT)
                        .setRowWidth(UiStyle.BUTTON_WIDTH)
                        .onChange(on -> {
                            GameSettings.setShowFpsInHud(on);
                            GameSettings.save();
                        }),
                ROW_SPACING * 1.5f);

        menu.add(new Button("Done").onClick(this::goBack));
    }

    private static String fpsCapLabel() {
        int cap = GameSettings.getFpsCap();
        return "FPS Cap: " + (cap == GameSettings.UNCAPPED_FPS ? "Unlimited" : String.valueOf(cap));
    }

    private void goBack() {
        this.game.openScreen(new OptionsScreen(this.game));
    }
}
