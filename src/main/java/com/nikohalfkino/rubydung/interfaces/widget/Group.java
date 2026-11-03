package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.render.Font;

/**
 * free-form container: every child is positioned by its own anchor and offset relative to the group's rectangle
 *
 * without a fixed size it's as big as its biggest child
 */
public class Group extends Container {

    public Group() {
    }

    public Group(float width, float height) {
        setSize(width, height);
    }

    @Override
    protected float contentWidth() {
        float max = 0.0f;
        for (Widget child : this.children) {
            if (child.isVisible()) {
                max = Math.max(max, child.getWidth());
            }
        }
        return max;
    }

    @Override
    protected float contentHeight() {
        float max = 0.0f;
        for (Widget child : this.children) {
            if (child.isVisible()) {
                max = Math.max(max, child.getHeight());
            }
        }
        return max;
    }

    @Override
    protected void arrange(Font font) {
        for (Widget child : this.children) {
            if (child.isVisible()) {
                child.placeIn(this.x, this.y, this.width, this.height);
                child.arrange(font);
            }
        }
    }
}
