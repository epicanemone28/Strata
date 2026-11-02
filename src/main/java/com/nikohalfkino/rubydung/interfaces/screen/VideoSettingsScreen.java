package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.Label;
import com.nikohalfkino.rubydung.interfaces.widget.Panel;
import com.nikohalfkino.rubydung.interfaces.widget.Slider;
import com.nikohalfkino.rubydung.interfaces.widget.Toggle;
import com.nikohalfkino.rubydung.util.GameSettings;
import org.lwjgl.opengl.Display;

/** Video settings, laid out like the Options screen: one panel with a dim header per section. */
public class VideoSettingsScreen extends MenuScreen {

    private static final float PANEL_WIDTH   = UiStyle.PANEL_WIDTH;
    private static final float PANEL_PADDING = 20.0f;
    private static final float PANEL_TOP     = 90.0f;

    private static final float ITEM_GAP    = 8.0f;
    private static final float HEADER_GAP  = 6.0f;
    private static final float SECTION_GAP = 16.0f;

    /** FPS cap slider: FPS_MIN..(FPS_UNLIMITED_AT - FPS_STEP) in steps, then "Unlimited" at the end. */
    private static final int FPS_MIN = 30;
    private static final int FPS_STEP = 5;
    private static final int FPS_UNLIMITED_AT = 245;

    /** Pixel size slider: each world pixel is PIXEL_MIN..PIXEL_MAX screen pixels wide. */
    private static final int PIXEL_MIN = 2;
    private static final int PIXEL_MAX = 12;

    private final Toggle vsyncToggle;
    private final Toggle pixelationToggle;

    public VideoSettingsScreen(RubyDung game) {
        super(game);

        setSwallowKeys(true);
        onEscape(this::goBack);

        addTitle("Video Settings");

        Panel panel = add(new Panel(ITEM_GAP), Anchor.TOP_CENTER, 0, PANEL_TOP);
        panel.setPadding(PANEL_PADDING);
        panel.setShadowAlpha(1.0f);
        panel.setCrossAlign(0.0f);

        // — Frame rate ————————————————————————————————————————
        panel.add(new Label("Frame Rate").setColor(UiStyle.TEXT_DIM), HEADER_GAP);

        this.vsyncToggle = panel.add(new Toggle("VSync", GameSettings.isVsyncEnabled(), Toggle.LabelSide.LEFT)
                .setRowWidth(PANEL_WIDTH)
                .onChange(on -> {
                    GameSettings.setVsyncEnabled(on);
                    Display.setVSyncEnabled(on);
                    GameSettings.save();
                }));

        // Greyed out and unclickable while VSync is on, since VSync already caps the frame rate.
        // Steps of FPS_STEP; the last step is "Unlimited".
        Slider fpsCap = panel.add(new Slider("FPS Cap", PANEL_WIDTH, UiStyle.ROW_HEIGHT,
                        FPS_MIN, FPS_UNLIMITED_AT, initialFpsSlider(), true)
                        .setFormatter(v -> {
                            int cap = snapFps(v);
                            return cap == GameSettings.UNCAPPED_FPS ? "Unlimited" : String.valueOf(cap);
                        })
                        .onChange(v -> GameSettings.setFpsCap(snapFps(v)))
                        .onCommit(GameSettings::save),
                ITEM_GAP);
        fpsCap.setEnabledWhen(() -> !this.vsyncToggle.getValue());

        // Explains the greyed-out button. The line is empty (not hidden) while VSync is off, so
        // the layout below it doesn't jump when VSync is toggled.
        panel.add(new Label(() -> this.vsyncToggle.getValue() ? "The FPS cap is ignored while VSync is on." : "")
                .setColor(UiStyle.TEXT_DIM), SECTION_GAP);

        // — World ————————————————————————————————————————————
        panel.add(new Label("World").setColor(UiStyle.TEXT_DIM), HEADER_GAP);

        this.pixelationToggle = panel.add(new Toggle("Pixelation", GameSettings.isPixelationEnabled(), Toggle.LabelSide.LEFT)
                .setRowWidth(PANEL_WIDTH)
                .onChange(on -> {
                    GameSettings.setPixelationEnabled(on);
                    GameSettings.save();
                }));

        // Greyed out and unclickable while pixelation is off.
        Slider pixelSize = panel.add(new Slider("Pixel Size", PANEL_WIDTH, UiStyle.ROW_HEIGHT,
                        PIXEL_MIN, PIXEL_MAX, initialPixelSlider(), true)
                        .setFormatter(v -> snapPixelSize(v) + "x")
                        .onChange(v -> GameSettings.setPixelSize(snapPixelSize(v)))
                        .onCommit(GameSettings::save),
                SECTION_GAP);
        pixelSize.setEnabledWhen(() -> this.pixelationToggle.getValue());

        // — Overlay ———————————————————————————————————————————
        panel.add(new Label("Overlay").setColor(UiStyle.TEXT_DIM), HEADER_GAP);

        panel.add(new Toggle("Show FPS", GameSettings.isShowFpsInHud(), Toggle.LabelSide.LEFT)
                        .setRowWidth(PANEL_WIDTH)
                        .onChange(on -> {
                            GameSettings.setShowFpsInHud(on);
                            GameSettings.save();
                        }),
                SECTION_GAP);

        // — Done ——————————————————————————————————————————————
        panel.add(new Button("Done", PANEL_WIDTH, UiStyle.BUTTON_HEIGHT, true)
                .onClick(this::goBack));
    }

    /** Slider position -> FPS cap: rounded to FPS_STEP, and the top of the track means no cap. */
    private static int snapFps(float value) {
        int snapped = Math.round(value / FPS_STEP) * FPS_STEP;
        return snapped >= FPS_UNLIMITED_AT ? GameSettings.UNCAPPED_FPS : Math.max(FPS_MIN, snapped);
    }

    /** Where the thumb starts for the saved cap (a saved value off the steps is clamped onto the track). */
    private static float initialFpsSlider() {
        int cap = GameSettings.getFpsCap();
        if (cap == GameSettings.UNCAPPED_FPS) {
            return FPS_UNLIMITED_AT;
        }
        return Math.max(FPS_MIN, Math.min(FPS_UNLIMITED_AT - FPS_STEP, cap));
    }

    /** Slider position -> pixel size, rounded to a whole number inside PIXEL_MIN..PIXEL_MAX. */
    private static int snapPixelSize(float value) {
        return Math.max(PIXEL_MIN, Math.min(PIXEL_MAX, Math.round(value)));
    }

    private static float initialPixelSlider() {
        return Math.max(PIXEL_MIN, Math.min(PIXEL_MAX, GameSettings.getPixelSize()));
    }

    private void goBack() {
        this.game.openScreen(new OptionsScreen(this.game));
    }
}