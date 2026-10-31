package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.render.Font;

import java.util.List;

/**
 * Lays its children out one after another along one axis, with a gap between them. Use
 * {@link Column} (top to bottom) or {@link Row} (left to right).
 * <ul>
 *   <li>Hidden children ({@link #setVisible} / {@link #setVisibleWhen}) take no space, so rows can
 *       appear and disappear and everything below slides up.</li>
 *   <li>A child's own offset still applies as a visual nudge, and {@link Widget#setGapAfter}
 *       overrides the gap that follows that one child.</li>
 *   <li>Across the stacking direction children are aligned with {@link #setCrossAlign}
 *       (default: centred).</li>
 *   <li>The stack itself is positioned like any other widget - give it an anchor and offset.</li>
 * </ul>
 */
public abstract class Stack extends Container {

    private final boolean vertical;
    private float gap;
    private float padding = 0.0f;
    private float crossAlign = 0.5f;

    protected Stack(boolean vertical, float gap) {
        this.vertical = vertical;
        this.gap = gap;
    }

    public void setGap(float gap) {
        this.gap = gap;
    }

    /** Empty space around the children, inside the stack's background / bounds. */
    public void setPadding(float padding) {
        this.padding = padding;
    }

    /**
     * Alignment across the stacking direction: 0 = left (Column) / top (Row), 0.5 = centred,
     * 1 = right / bottom. Only matters when children differ in size.
     */
    public void setCrossAlign(float crossAlign) {
        this.crossAlign = crossAlign;
    }

    public void setCrossAlign(Anchor anchor) {
        this.crossAlign = this.vertical ? anchor.fx : anchor.fy;
    }

    private float gapAfter(Widget child) {
        return child.hasGapAfter() ? child.getGapAfter() : this.gap;
    }

    private float main(Widget child) {
        return this.vertical ? child.getHeight() : child.getWidth();
    }

    private float cross(Widget child) {
        return this.vertical ? child.getWidth() : child.getHeight();
    }

    private float mainContent() {
        List<Widget> visible = visibleChildren();
        float total = 0.0f;
        for (int i = 0; i < visible.size(); i++) {
            Widget child = visible.get(i);
            total += main(child);
            if (i < visible.size() - 1) {
                total += gapAfter(child);
            }
        }
        return total + this.padding * 2.0f;
    }

    private float crossContent() {
        float max = 0.0f;
        for (Widget child : visibleChildren()) {
            max = Math.max(max, cross(child));
        }
        return max + this.padding * 2.0f;
    }

    @Override
    protected float contentWidth() {
        return this.vertical ? crossContent() : mainContent();
    }

    @Override
    protected float contentHeight() {
        return this.vertical ? mainContent() : crossContent();
    }

    @Override
    protected void arrange(Font font) {
        float cursor = (this.vertical ? this.y : this.x) + this.padding;
        float crossSpace = (this.vertical ? this.width : this.height) - this.padding * 2.0f;

        List<Widget> visible = visibleChildren();
        for (Widget child : visible) {
            float crossPos = this.padding + (crossSpace - cross(child)) * this.crossAlign;

            if (this.vertical) {
                child.setPosition(this.x + crossPos + child.getOffsetX(), cursor + child.getOffsetY());
            } else {
                child.setPosition(cursor + child.getOffsetX(), this.y + crossPos + child.getOffsetY());
            }
            child.arrange(font);

            cursor += main(child) + gapAfter(child);
        }
    }
}
