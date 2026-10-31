package com.nikohalfkino.rubydung.interfaces;

import com.nikohalfkino.rubydung.interfaces.widget.PanelSection;

import java.util.Collections;
import java.util.List;

public interface FrameScreen {

    void setLayout(GameFrame.Layout layout);

    boolean showsSlots();

    String getPanelTitle();

    int getFrameTint();

    /**
     * What the right-hand panel is made of, top to bottom (see {@link PanelSection}). The default
     * is the classic single block: one slot grid, or one plain panel if {@link #showsSlots()} is
     * false. Override to stack several blocks. The matching rectangles arrive in
     * {@link GameFrame.Layout#sections}, in the same order.
     */
    default List<PanelSection> getSections() {
        return Collections.singletonList(showsSlots()
                ? PanelSection.slots(getPanelTitle(), GameFrame.GRID_COLUMNS, GameFrame.GRID_ROWS)
                : PanelSection.fill(getPanelTitle()));
    }
}