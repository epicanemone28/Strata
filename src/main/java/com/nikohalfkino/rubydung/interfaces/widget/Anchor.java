package com.nikohalfkino.rubydung.interfaces.widget;

/**
 * where a widget is pinned inside its parent: its matching point is lined up with the parent's (TOP_RIGHT puts its top-right corner on the parent's, CENTER centres it)
 *
 * the offset then nudges it from there (positive = right / down)
 */
public enum Anchor {

    TOP_LEFT(0.0f, 0.0f),
    TOP_CENTER(0.5f, 0.0f),
    TOP_RIGHT(1.0f, 0.0f),

    CENTER_LEFT(0.0f, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1.0f, 0.5f),

    BOTTOM_LEFT(0.0f, 1.0f),
    BOTTOM_CENTER(0.5f, 1.0f),
    BOTTOM_RIGHT(1.0f, 1.0f);

    /** 0 = left edge, 1 = right edge */
    public final float fx;
    /** 0 = top edge, 1 = bottom edge */
    public final float fy;

    Anchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }
}
