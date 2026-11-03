package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.render.Font;

/** draws nothing, just takes up room in a Column / Row */
public class Spacer extends Widget {

    public Spacer(float width, float height) {
        setSize(width, height);
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
    }
}
