package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.render.Font;

/**
 * A free-form container: every child is positioned on its own by its anchor and offset, relative
 * to this group's rectangle. Without a fixed size the group is as big as its biggest child.
 * <pre>
 *     group.add(new Label("Top left"), Anchor.TOP_LEFT, 10, 10);
 *     group.add(new Label("Bottom right"), Anchor.BOTTOM_RIGHT, -10, -10);
 * </pre>
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
