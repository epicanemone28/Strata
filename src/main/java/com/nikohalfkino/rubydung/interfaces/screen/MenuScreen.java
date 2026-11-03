package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.Screen;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Label;

/** base for full-window menu screens: tiled background plus shortcuts for the heading and the centred column of controls */
public abstract class MenuScreen extends Screen {

    protected final RubyDung game;

    protected MenuScreen(RubyDung game) {
        super(game.getFont());
        this.game = game;
    }

    @Override
    protected void renderBackground() {
        MenuBackground.render(this.width, this.height);
    }

    /** heading centred at the top, y pixels down */
    protected Label addTitle(String text, float y) {
        return add(new Label(text), Anchor.TOP_CENTER, 0.0f, y);
    }

    protected Label addTitle(String text) {
        return addTitle(text, UiStyle.TITLE_Y);
    }

    /** centred column whose top edge is y pixels down, add the controls to it */
    protected Column addColumn(float gap, float y) {
        return add(new Column(gap), Anchor.TOP_CENTER, 0.0f, y);
    }
}
