package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.render.Font;

/** Draws nothing; just takes up room in a {@link Column} / {@link Row}. */
public class Spacer extends Widget {

    public Spacer(float width, float height) {
        setSize(width, height);
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
    }
}
