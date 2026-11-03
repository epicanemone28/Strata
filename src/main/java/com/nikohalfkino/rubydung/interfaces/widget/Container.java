package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.render.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * a widget that holds other widgets, subclasses decide placement: Group (each child anchored on its own), Column / Row (stacked)
 *
 * add(...) returns the widget you passed in so it can be stored in a field in the same line
 */
public abstract class Container extends Widget {

    protected final List<Widget> children = new ArrayList<>();

    // Optional background drawn behind the children.
    private boolean hasBackground = false;
    private float bgR, bgG, bgB, bgA;

    // registration

    public <T extends Widget> T add(T child) {
        child.parent = this;
        this.children.add(child);
        return child;
    }

    /** pinned to anchor of this container, nudged by (dx, dy). only meaningful in a Group */
    public <T extends Widget> T add(T child, Anchor anchor, float dx, float dy) {
        child.setAnchor(anchor, dx, dy);
        return add(child);
    }

    /** custom gap after the child. only meaningful in a Column / Row */
    public <T extends Widget> T add(T child, float gapAfter) {
        child.setGapAfter(gapAfter);
        return add(child);
    }

    public void remove(Widget child) {
        if (this.children.remove(child)) {
            child.parent = null;
        }
    }

    public void clear() {
        for (Widget child : this.children) {
            child.parent = null;
        }
        this.children.clear();
    }

    public List<Widget> getChildren() {
        return this.children;
    }

    /** flat translucent colour behind the children */
    public void setBackground(float r, float g, float b, float a) {
        this.hasBackground = true;
        this.bgR = r;
        this.bgG = g;
        this.bgB = b;
        this.bgA = a;
    }

    // layout

    protected List<Widget> visibleChildren() {
        List<Widget> visible = new ArrayList<>(this.children.size());
        for (Widget child : this.children) {
            if (child.isVisible()) {
                visible.add(child);
            }
        }
        return visible;
    }

    @Override
    public void measure(Font font) {
        for (Widget child : this.children) {
            if (child.isVisible()) {
                child.measure(font);
            }
        }
        this.width = getPrefWidth() >= 0.0f ? getPrefWidth() : contentWidth();
        this.height = getPrefHeight() >= 0.0f ? getPrefHeight() : contentHeight();
    }

    /** size needed to hold the (already measured) visible children */
    protected abstract float contentWidth();

    protected abstract float contentHeight();

    /** lays this container out as the top of a widget tree in the given rectangle, used by Screen and Hud for their root group */
    public final void layoutRoot(Font font, float rootX, float rootY, float rootWidth, float rootHeight) {
        setSize(rootWidth, rootHeight);
        this.width = rootWidth;
        this.height = rootHeight;
        measure(font);
        setPosition(rootX, rootY);
        arrange(font);
    }

    // rendering

    @Override
    protected final void draw(Font font, int mouseX, int mouseY) {
        if (this.hasBackground) {
            com.nikohalfkino.rubydung.interfaces.Gui2D.fillRect(this.x, this.y, this.width, this.height,
                    this.bgR, this.bgG, this.bgB, this.bgA);
        }
        drawSelf(font, mouseX, mouseY);
        for (Widget child : this.children) {
            child.render(font, mouseX, mouseY);
        }
    }

    /** hook for containers that draw something behind their children */
    protected void drawSelf(Font font, int mouseX, int mouseY) {
    }

    // input

    @Override
    public Widget pick(int mouseX, int mouseY) {
        if (!isVisible() || !isEnabled()) {
            return null;
        }
        // Later children are drawn on top, so they get the click first.
        for (int i = this.children.size() - 1; i >= 0; i--) {
            Widget hit = this.children.get(i).pick(mouseX, mouseY);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    @Override
    public void collectFocusable(List<Widget> out) {
        if (!isVisible()) {
            return;
        }
        for (Widget child : this.children) {
            child.collectFocusable(out);
        }
    }
}
