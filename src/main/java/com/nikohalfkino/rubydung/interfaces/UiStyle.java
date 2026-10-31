package com.nikohalfkino.rubydung.interfaces;

/**
 * Shared sizes, spacing and colours for the UI, in one place. Change a value here and every menu
 * that uses it follows; a screen that needs something different just passes its own number.
 */
public final class UiStyle {

    private UiStyle() {
    }

    // Standard menu button.
    public static final float BUTTON_WIDTH = 200.0f;
    public static final float BUTTON_HEIGHT = 30.0f;
    public static final float BUTTON_GAP = 10.0f;

    // Slider / text field rows.
    public static final float ROW_WIDTH = 300.0f;
    public static final float ROW_HEIGHT = 28.0f;

    // Wide panels (world list, controls, new-world form).
    public static final float PANEL_WIDTH = 420.0f;

    // Where menu screens put their heading, and where their content starts.
    public static final float TITLE_Y = 40.0f;
    public static final float CONTENT_Y = 100.0f;

    // Text colours (0xRRGGBB).
    public static final int TEXT = 0xFFFFFF;
    public static final int TEXT_HOVER = 0xFFFF00;
    public static final int TEXT_DISABLED = 0x888888;
    public static final int TEXT_DIM = 0xAAAAAA;
    public static final int TEXT_ERROR = 0xFF5555;
    public static final int TEXT_DANGER = 0xFF8888;

    /** Height of one line of normal text. */
    public static final float LINE_HEIGHT = 16.0f;
}
