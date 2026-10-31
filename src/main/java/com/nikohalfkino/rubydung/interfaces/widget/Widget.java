package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.render.Font;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Base class of everything on screen - menu buttons, labels, HUD elements, whole groups of those.
 * <p>
 * <b>Positioning.</b> A widget is positioned by an {@link Anchor} inside its parent plus an offset
 * (see {@link #setAnchor}); inside a {@link Column} / {@link Row} the container stacks it instead
 * and only the offset is used, as a nudge. Nothing here is ever positioned with absolute screen
 * coordinates, so layouts follow the window size by themselves. For "half way down the screen"
 * style placement use {@link #setOffsetFraction}.
 * <p>
 * <b>Frame cycle.</b> Every frame the owner (a {@code Screen} or the {@code Hud}) runs
 * {@code measure} (bottom-up: every widget works out its size) and then {@code arrange}
 * (top-down: every widget gets its position), then calls {@link #render}.
 * <p>
 * <b>Sizes.</b> {@link #setSize} fixes a size; a negative value means "work it out" (text width
 * for a label, the children's extent for a container).
 */
public abstract class Widget {

    Widget parent;

    /** Resolved bounds, valid after the last layout pass. Origin is the window's top-left. */
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

    // ---------------------------------------------------------------------------------------
    // Positioning
    // ---------------------------------------------------------------------------------------

    /** Pins this widget to {@code anchor} of its parent, then nudges it by (dx, dy) pixels. */
    public void setAnchor(Anchor anchor, float dx, float dy) {
        this.anchor = anchor;
        this.offsetX = dx;
        this.offsetY = dy;
    }

    /** Just the nudge, keeping the current anchor. */
    public void setOffset(float dx, float dy) {
        this.offsetX = dx;
        this.offsetY = dy;
    }

    /**
     * Adds a share of the parent's size to the offset: (0, 0.25) moves the widget down by a quarter
     * of the parent's height. Handy for "25% from the top" placement.
     */
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

    /** Fixed size; pass a negative number for either value to size that axis automatically. */
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

    /**
     * Sets the resolved position directly. Containers use this to place their children; widgets
     * that are never put in a container can also be positioned manually with it.
     */
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

    /**
     * Space between this widget and the next one inside a {@link Column} / {@link Row}, replacing
     * the container's default gap. May be negative to tuck widgets closer together.
     */
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

    // ---------------------------------------------------------------------------------------
    // Visibility / enabled
    // ---------------------------------------------------------------------------------------

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** Visible whenever the condition holds (re-evaluated every frame). Hidden widgets take no space in a Column/Row. */
    public void setVisibleWhen(BooleanSupplier condition) {
        this.visibleWhen = condition;
    }

    public boolean isVisible() {
        return this.visible && (this.visibleWhen == null || this.visibleWhen.getAsBoolean());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Enabled whenever the condition holds (re-evaluated every frame). */
    public void setEnabledWhen(BooleanSupplier condition) {
        this.enabledWhen = condition;
    }

    public boolean isEnabled() {
        return this.enabled && (this.enabledWhen == null || this.enabledWhen.getAsBoolean());
    }

    // ---------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------

    /** First layout pass: work out {@link #width} and {@link #height}. */
    public void measure(Font font) {
        this.width = this.prefWidth >= 0.0f ? this.prefWidth : naturalWidth(font);
        this.height = this.prefHeight >= 0.0f ? this.prefHeight : naturalHeight(font);
    }

    /** Size to use when no fixed width was set. */
    protected float naturalWidth(Font font) {
        return 0.0f;
    }

    /** Size to use when no fixed height was set. */
    protected float naturalHeight(Font font) {
        return 0.0f;
    }

    /** Second layout pass: place children. The widget's own position is already set. */
    protected void arrange(Font font) {
    }

    /** Resolves this widget's position from its anchor inside the given parent rectangle. */
    final void placeIn(float parentX, float parentY, float parentWidth, float parentHeight) {
        this.x = parentX + (parentWidth - this.width) * this.anchor.fx + this.offsetX + this.fractionX * parentWidth;
        this.y = parentY + (parentHeight - this.height) * this.anchor.fy + this.offsetY + this.fractionY * parentHeight;
    }

    /** Width of the top-most widget in the tree - the window / screen size the UI is laid out in. */
    public float getRootWidth() {
        Widget w = this;
        while (w.parent != null) {
            w = w.parent;
        }
        return w.width;
    }

    /** Height of the top-most widget in the tree. */
    public float getRootHeight() {
        Widget w = this;
        while (w.parent != null) {
            w = w.parent;
        }
        return w.height;
    }

    // ---------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------

    /** Draws the widget (if visible). Mouse coordinates are only used for hover styling. */
    public final void render(Font font, int mouseX, int mouseY) {
        if (isVisible()) {
            draw(font, mouseX, mouseY);
        }
    }

    protected abstract void draw(Font font, int mouseX, int mouseY);

    // ---------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------

    /** Whether the point is inside the widget (edges included). */
    public boolean contains(float mouseX, float mouseY) {
        return mouseX >= this.x && mouseX <= this.x + this.width
                && mouseY >= this.y && mouseY <= this.y + this.height;
    }

    /** Same as {@link #contains}; the name widgets use for hover styling. */
    public boolean isHovered(int mouseX, int mouseY) {
        return contains(mouseX, mouseY);
    }

    /** Whether clicks should reach this widget at all (labels with no action, spacers: no). */
    protected boolean isInteractive() {
        return false;
    }

    /** The top-most interactive widget under the mouse, or null. */
    public Widget pick(int mouseX, int mouseY) {
        if (isVisible() && isEnabled() && isInteractive() && contains(mouseX, mouseY)) {
            return this;
        }
        return null;
    }

    /** Called for the widget {@link #pick} returned, on a left click. */
    public void onMouseClicked(int mouseX, int mouseY) {
    }

    // Focus ------------------------------------------------------------------------------------

    /** Whether this widget can receive keyboard input (text inputs). */
    public boolean isFocusable() {
        return false;
    }

    public final boolean isFocused() {
        return this.focused;
    }

    /** Called by the owning Screen. */
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

    /** Lets a focused widget ask the Screen to take focus away from it (e.g. on Enter). */
    protected final void requestBlur() {
        this.blurRequested = true;
    }

    public final boolean takeBlurRequest() {
        boolean requested = this.blurRequested;
        this.blurRequested = false;
        return requested;
    }

    /** Offered to the focused widget first. Return true to swallow the key. */
    public boolean keyPressed(int key, char character) {
        return false;
    }

    /** Adds this widget (and any children) to {@code out} if it can take focus, in tree order. */
    public void collectFocusable(List<Widget> out) {
        if (isVisible() && isEnabled() && isFocusable()) {
            out.add(this);
        }
    }
}
