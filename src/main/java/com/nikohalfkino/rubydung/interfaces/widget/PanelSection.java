package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.FrameScreen;
import com.nikohalfkino.rubydung.interfaces.GameFrame;

/**
 * Describes one block of a {@link FrameScreen}'s right-hand panel: a heading over a sunken box.
 * <p>
 * A screen returns a list of these from {@link FrameScreen#getSections()} and {@link GameFrame}
 * stacks them under the portrait, draws the headings, boxes and (for slot sections) the slot
 * cells, and hands the resolved rectangles back as {@link GameFrame.SectionLayout}s.
 * <ul>
 *   <li>{@link #slots} - a grid of slots. {@code rows} is a maximum: if the window is too short
 *       the frame uses fewer rows (never less than one). If columns equals rows the grid is a
 *       square and stays one when it shrinks.</li>
 *   <li>{@link #fill} - a plain panel that takes all the vertical space the slot sections leave
 *       over (user list, menu buttons, ...).</li>
 * </ul>
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