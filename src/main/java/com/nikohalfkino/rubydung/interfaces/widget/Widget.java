package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.render.Font;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * base class of everything on screen: buttons, labels, HUD elements, whole groups
 *
 * position: an Anchor inside the parent plus an offset (see setAnchor), inside a Column / Row the container stacks it and only the offset is used, as a nudge. never absolute screen coordinates so layouts follow the window size, use setOffsetFraction for "half way down"
 *
 * frame: the owner (Screen or Hud) runs measure (bottom-up, sizes) then arrange (top-down, positions), then render
 *
 * size: setSize fixes it, negative = work it out (text width for a label, children's extent for a container)
 */
public abstract class Widget {

    Widget parent;

    // resolved bounds, valid after the last layout pass (origin is the window's top-left)
    protected float x;
    protected float y;
    protected float width;
    protected float height;

    private float prefWidth = -1.0f;
    private float prefHeight = -1.0f;

    private Anchor anchor = Anchor.TOP_LEFT;
    private float offsetX;
    private float offsetY;
    private float fractionX;
    private float fractionY;

    private boolean hasGapAfter = false;
    private float gapAfter = 0.0f;

    private boolean visible = true;
    private BooleanSupplier visibleWhen;
    private boolean enabled = true;
    private BooleanSupplier enabledWhen;

    private boolean focused = false;
    private boolean blurRequested = false;

    // positioning

    /** pins to anchor of the parent, then nudges by (dx, dy) pixels */
    public void setAnchor(Anchor anchor, float dx, float dy) {
        this.anchor = anchor;
        this.offsetX = dx;
        this.offsetY = dy;
    }

    /** just the nudge, keeps the current anchor */
    public void setOffset(float dx, float dy) {
        this.offsetX = dx;
        this.offsetY = dy;
    }

    /** adds a share of the parent's size to the offset, (0, 0.25) = down by a quarter of the parent's height */
    public void setOffsetFraction(float fx, float fy) {
        this.fractionX = fx;
        this.fractionY = fy;
    }

    public Anchor getAnchor() {
        return this.anchor;
    }

    public float getOffsetX() {
        return this.offsetX;
    }

    public float getOffsetY() {
        return this.offsetY;
    }

    /** negative = size that axis automatically */
    public void setSize(float width, float height) {
        this.prefWidth = width;
        this.prefHeight = height;
    }

    public float getPrefWidth() {
        return this.prefWidth;
    }

    public float getPrefHeight() {
        return this.prefHeight;
    }

    /** sets the resolved position directly, containers use it to place their children (or position a widget by hand) */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }

    /** gap between this and the next widget in a Column / Row instead of the default, can be negative */
    public void setGapAfter(float gap) {
        this.hasGapAfter = true;
        this.gapAfter = gap;
    }

    boolean hasGapAfter() {
        return this.hasGapAfter;
    }

    float getGapAfter() {
        return this.gapAfter;
    }

    // visibility / enabled

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** re-evaluated every frame, hidden widgets take no space in a Column / Row */
    public void setVisibleWhen(BooleanSupplier condition) {
        this.visibleWhen = condition;
    }

    public boolean isVisible() {
        return this.visible && (this.visibleWhen == null || this.visibleWhen.getAsBoolean());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** re-evaluated every frame */
    public void setEnabledWhen(BooleanSupplier condition) {
        this.enabledWhen = condition;
    }

    public boolean isEnabled() {
        return this.enabled && (this.enabledWhen == null || this.enabledWhen.getAsBoolean());
    }

    // layout

    /** first layout pass: works out width and height */
    public void measure(Font font) {
        this.width = this.prefWidth >= 0.0f ? this.prefWidth : naturalWidth(font);
        this.height = this.prefHeight >= 0.0f ? this.prefHeight : naturalHeight(font);
    }

    /** size to use when no fixed width was set */
    protected float naturalWidth(Font font) {
        return 0.0f;
    }

    /** size to use when no fixed height was set */
    protected float naturalHeight(Font font) {
        return 0.0f;
    }

    /** second layout pass: places children, the widget's own position is already set */
    protected void arrange(Font font) {
    }

    /** resolves the position from the anchor inside the given parent rectangle */
    final void placeIn(float parentX, float parentY, float parentWidth, float parentHeight) {
        this.x = parentX + (parentWidth - this.width) * this.anchor.fx + this.offsetX + this.fractionX * parentWidth;
        this.y = parentY + (parentHeight - this.height) * this.anchor.fy + this.offsetY + this.fractionY * parentHeight;
    }

    /** width of the top-most widget in the tree, i.e. the size the UI is laid out in */
    public float getRootWidth() {
        Widget w = this;
        while (w.parent != null) {
            w = w.parent;
        }
        return w.width;
    }

    public float getRootHeight() {
        Widget w = this;
        while (w.parent != null) {
            w = w.parent;
        }
        return w.height;
    }

    // rendering

    /** draws if visible, the mouse is only used for hover styling */
    public final void render(Font font, int mouseX, int mouseY) {
        if (isVisible()) {
            draw(font, mouseX, mouseY);
        }
    }

    protected abstract void draw(Font font, int mouseX, int mouseY);

    // input

    /** edges included */
    public boolean contains(float mouseX, float mouseY) {
        return mouseX >= this.x && mouseX <= this.x + this.width
                && mouseY >= this.y && mouseY <= this.y + this.height;
    }

    /** same as contains(), the name widgets use for hover styling */
    public boolean isHovered(int mouseX, int mouseY) {
        return contains(mouseX, mouseY);
    }

    /** whether clicks reach this widget at all (labels with no action, spacers: no) */
    protected boolean isInteractive() {
        return false;
    }

    /** top-most interactive widget under the mouse, or null */
    public Widget pick(int mouseX, int mouseY) {
        if (isVisible() && isEnabled() && isInteractive() && contains(mouseX, mouseY)) {
            return this;
        }
        return null;
    }

    /** called for the widget pick() returned, on a left click */
    public void onMouseClicked(int mouseX, int mouseY) {
    }

    // focus

    /** whether it can receive keyboard input (text inputs) */
    public boolean isFocusable() {
        return false;
    }

    public final boolean isFocused() {
        return this.focused;
    }

    /** called by the owning Screen */
    public final void setFocused(boolean focused) {
        if (this.focused == focused) {
            return;
        }
        this.focused = focused;
        if (focused) {
            this.blurRequested = false;
            onFocus();
        } else {
            onBlur();
        }
    }

    protected void onFocus() {
    }

    protected void onBlur() {
    }

    /** asks the Screen to take focus away (e.g. on Enter) */
    protected final void requestBlur() {
        this.blurRequested = true;
    }

    public final boolean takeBlurRequest() {
        boolean requested = this.blurRequested;
        this.blurRequested = false;
        return requested;
    }

    /** offered to the focused widget first, return true to swallow the key */
    public boolean keyPressed(int key, char character) {
        return false;
    }

    /** adds this widget and its children to out if they can take focus, in tree order */
    public void collectFocusable(List<Widget> out) {
        if (isVisible() && isEnabled() && isFocusable()) {
            out.add(this);
        }
    }
}
