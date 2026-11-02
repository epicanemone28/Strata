package com.nikohalfkino.rubydung.interfaces;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;

/**
 * Base class for screens shown inside the {@link GameFrame}. It keeps the frame's current
 * {@link GameFrame.Layout}, and every frame - before the widget tree is laid out - lets the
 * subclass pin its widgets to the frame's rectangles (a panel section, the input bar, ...), so
 * widgets follow the open / close animation and window resizes by themselves.
 * <p>
 * Usage: {@code add(widget, Anchor.TOP_LEFT, 0, 0)} in the constructor as for any Screen, then in
 * {@link #placeWidgets()} call {@link #placeInSection} or {@link #place} for each of them.
 * Nothing is drawn or clicked until the frame has provided a layout.
 */
public abstract class FramePanelScreen extends Screen implements FrameScreen {

    /** Height of the corner button ("Done" / "Quit"), in real pixels; its width is the panel's. */
    private static final float CORNER_BUTTON_HEIGHT = 28.0F;

    /** Distance from the bottom of the world view to the middle of the bar under it (the chat input's centre line). */
    private static final float BOTTOM_BAR_CENTER = 32.0F;

    protected final RubyDung game;

    private Button cornerButton;

    /** Set by GameRenderer every frame from the frame's current (animated) layout. */
    protected GameFrame.Layout layout;

    protected FramePanelScreen(RubyDung game) {
        super(game.getFont());
        this.game = game;
    }

    @Override
    public void setLayout(GameFrame.Layout layout) {
        this.layout = layout;
    }

    /** Whether the frame has finished sliding open. */
    protected boolean isFrameOpen() {
        return this.layout != null && this.layout.openness >= 1.0F;
    }

    /**
     * Adds a button in the bottom-right corner of the frame: in the bar under the world view,
     * as wide as the right-hand panel and level with the chat input line. It is pinned
     * to the frame every frame, so it slides in and out with the panel. Call once, in the
     * constructor ("Done" for screens you just close, "Quit" for the pause menu).
     */
    protected final Button addCornerButton(String label, Runnable onClick) {
        this.cornerButton = add(new Button(label, 0.0F, CORNER_BUTTON_HEIGHT, true)
                .onClick(onClick), Anchor.TOP_LEFT, 0, 0);
        return this.cornerButton;
    }

    private void placeCornerButton() {
        if (this.cornerButton == null) {
            return;
        }

        // As wide as the panel above it, flush with its left and right edges.
        float y = this.layout.viewY + this.layout.viewHeight + BOTTOM_BAR_CENTER - CORNER_BUTTON_HEIGHT / 2.0F;
        place(this.cornerButton, this.layout.panelX, y, this.layout.panelWidth, CORNER_BUTTON_HEIGHT);
    }

    /** Called every frame while a layout exists, before the widgets are measured. */
    protected abstract void placeWidgets();

    /** Pins the widget to the box of panel section {@code index}; hides it if the frame has no such section. */
    protected final void placeInSection(Widget widget, int index) {
        if (this.layout == null || index < 0 || index >= this.layout.sections.size()) {
            widget.setVisible(false);
            return;
        }

        GameFrame.SectionLayout section = this.layout.sections.get(index);
        widget.setVisible(true);
        place(widget, section.x, section.y, section.width, section.height);
    }

    /**
     * Centres a widget (at its natural size) in a window rectangle. The widget is measured here, so
     * it is centred correctly on the very first frame.
     */
    protected final void placeCentered(Widget widget, float x, float y, float width, float height) {
        widget.measure(this.font);
        widget.setAnchor(Anchor.TOP_LEFT,
                x + (width - widget.getWidth()) / 2.0F,
                y + (height - widget.getHeight()) / 2.0F);
    }

    /** Centres a widget in the box of panel section {@code index}; hides it if there is no such section. */
    protected final void placeCenteredInSection(Widget widget, int index) {
        if (this.layout == null || index < 0 || index >= this.layout.sections.size()) {
            widget.setVisible(false);
            return;
        }

        GameFrame.SectionLayout section = this.layout.sections.get(index);
        widget.setVisible(true);
        placeCentered(widget, section.x, section.y, section.width, section.height);
    }

    /** Pins the widget to an absolute window rectangle. */
    protected final void place(Widget widget, float x, float y, float width, float height) {
        widget.setAnchor(Anchor.TOP_LEFT, x, y);
        widget.setSize(width, height);
    }

    @Override
    protected void layoutRoot() {
        if (this.layout != null) {
            placeCornerButton();
            placeWidgets();
        }
        this.root.layoutRoot(this.font, 0, 0, this.game.getWidth(), this.game.getHeight());
    }

    @Override
    public void render(int mouseX, int mouseY) {
        if (this.layout == null) {
            return;
        }
        super.render(mouseX, mouseY);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY) {
        if (this.layout == null) {
            return;
        }
        super.mouseClicked(mouseX, mouseY);
    }
}