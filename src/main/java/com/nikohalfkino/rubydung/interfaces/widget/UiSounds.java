package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.audio.SoundEffects;

/** the sounds the UI widgets share (sfx/ui/) */
public final class UiSounds {

    private static final String HOVER = "ui/btn_hover";
    private static final String CLICK = "ui/btn_click";
    private static final String KEYPRESS = "ui/keypress";

    static {
        // Decoded up front so the first hover, click or keypress has no delay.
        SoundEffects.preload(HOVER, CLICK, KEYPRESS);
    }

    private UiSounds() {}

    /** cursor just moved onto an enabled widget */
    public static void hover() {
        SoundEffects.play(HOVER);
    }

    /** an enabled widget was clicked */
    public static void click() {
        SoundEffects.play(CLICK);
    }

    /** a key changed the text in a text box (random pitch) */
    public static void keyPress() {
        SoundEffects.playVaried(KEYPRESS);
    }
}
