package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.FrameScreen;
import com.nikohalfkino.rubydung.interfaces.GameFrame;

/**
 * one block of a FrameScreen's right-hand panel: a heading over a sunken box. screens return a list of these from getSections(), GameFrame stacks them under the portrait and hands the rects back as GameFrame.SectionLayout
 *
 * slots: a slot grid, rows is a maximum (fewer if the window is short, never below one), columns == rows stays a square when it shrinks
 *
 * fill: a plain panel that takes the vertical space the slot sections leave (user list, menu buttons, ...)
 */
public final class PanelSection {

    public final String title;
    public final boolean slots;
    public final int columns;
    public final int rows;

    private PanelSection(String title, boolean slots, int columns, int rows) {
        this.title = title == null ? "" : title;
        this.slots = slots;
        this.columns = columns;
        this.rows = rows;
    }

    public static PanelSection slots(String title, int columns, int maxRows) {
        return new PanelSection(title, true, columns, maxRows);
    }

    public static PanelSection fill(String title) {
        return new PanelSection(title, false, 0, 0);
    }
}