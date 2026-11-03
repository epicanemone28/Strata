package com.nikohalfkino.rubydung.interfaces;

import com.nikohalfkino.rubydung.interfaces.widget.PanelSection;

import java.util.Collections;
import java.util.List;

public interface FrameScreen {

    void setLayout(GameFrame.Layout layout);

    boolean showsSlots();

    String getPanelTitle();

    int getFrameTint();

    /** right-hand panel blocks, top to bottom. default is one slot grid (or one plain panel if showsSlots() is false), the rects arrive in GameFrame.Layout.sections */
    default List<PanelSection> getSections() {
        return Collections.singletonList(showsSlots()
                ? PanelSection.slots(getPanelTitle(), GameFrame.GRID_COLUMNS, GameFrame.GRID_ROWS)
                : PanelSection.fill(getPanelTitle()));
    }
}