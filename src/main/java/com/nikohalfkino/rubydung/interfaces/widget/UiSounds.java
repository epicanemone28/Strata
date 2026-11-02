package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.audio.SoundEffects;

/** The sounds the UI widgets share (files in sfx/ui/). */
public final class UiSounds {

    private static final String HOVER = "ui/btn_hover";
    private static final String CLICK = "ui/btn_click";
    private static final String KEYPRESS = "ui/keypress";

    static {
        // Decoded up front so the first hover, click or keypress has no delay.
        SoundEffects.preload(HOVER, CLICK, KEYPRESS);
    }

    private UiSounds() {}

    /** The cursor just moved onto an enabled widget. */
    public static void hover() {
        SoundEffects.play(HOVER);
    }

    /** An enabled widget was clicked. */
    public static void click() {
        SoundEffects.play(CLICK);
    }

    /** A key changed the text in a text box (random pitch each time). */
    public static void keyPress() {
        SoundEffects.playVaried(KEYPRESS);
    }
}
