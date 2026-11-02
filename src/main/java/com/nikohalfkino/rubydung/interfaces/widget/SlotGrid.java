package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.GameFrame;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.render.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A scrollable grid of slots holding items of type {@code T}. The slot cells themselves are drawn
 * by {@link GameFrame}; this widget draws what goes in them (hover / selection highlight, an icon
 * per item, an optional text overlay) and handles clicking and scrolling.
 * <p>
 * The number of columns and rows comes from the widget's size ({@link #setSize}) divided by the
 * slot size, so it simply fills whatever rectangle it is given - in practice the
 * {@link GameFrame.SectionLayout} it sits on. Position it with the usual anchor / offset, or let a
 * {@code FramePanelScreen} do it.
 */
public class SlotGrid<T> extends Widget {

    /** Draws one item's icon, centred on ({@code centerX}, {@code centerY}). Runs outside the 2D pass, so it may use any GL state. */
    public interface IconDrawer<T> {
        void draw(T item, float centerX, float centerY, float size, int windowWidth, int windowHeight);
    }

    /** Draws text / shapes on top of the slot at ({@code slotX}, {@code slotY}). Runs inside the 2D pass. */
    public interface OverlayDrawer<T> {
        void draw(Font font, T item, int slotX, int slotY, int slotSize);
    }

    private static final float DEFAULT_ICON_FRACTION = 0.5F;

    private final int slotSize;

    private List<T> items = new ArrayList<>();
    private int columns = 1;
    private int rows = 1;
    private int scrollRow = 0;

    private Predicate<T> selected = item -> false;
    private Consumer<T> onSelect = item -> { };
    private IconDrawer<T> iconDrawer;
    private OverlayDrawer<T> overlayDrawer;
    private float iconFraction = DEFAULT_ICON_FRACTION;

    public SlotGrid() {
        this(GameFrame.SLOT_PX);
    }

    public SlotGrid(int slotSize) {
        this.slotSize = slotSize;
    }

    // ---------------------------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------------------------

    public SlotGrid<T> setItems(List<T> items) {
        this.items = new ArrayList<>(items);
        clampScroll();
        return this;
    }

    /** Which items get the yellow selection outline. */
    public SlotGrid<T> selectedWhen(Predicate<T> selected) {
        this.selected = selected;
        return this;
    }

    /** Called when an item's slot is clicked. */
    public SlotGrid<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public SlotGrid<T> setIconDrawer(IconDrawer<T> iconDrawer) {
        this.iconDrawer = iconDrawer;
        return this;
    }

    public SlotGrid<T> setOverlayDrawer(OverlayDrawer<T> overlayDrawer) {
        this.overlayDrawer = overlayDrawer;
        return this;
    }

    /** Icon size as a fraction of the slot size. */
    public SlotGrid<T> setIconFraction(float fraction) {
        this.iconFraction = fraction;
        return this;
    }

    public int getColumns() {
        return this.columns;
    }

    public int getRows() {
        return this.rows;
    }

    // ---------------------------------------------------------------------------------------
    // Scrolling / lookup
    // ---------------------------------------------------------------------------------------

    /** Scrolls by whole rows (positive = down), clamped to the content. */
    public void scroll(int rowsToScroll) {
        this.scrollRow += rowsToScroll;
        clampScroll();
    }

    private void clampScroll() {
        int totalRows = (this.items.size() + this.columns - 1) / this.columns;
        int maxScroll = Math.max(0, totalRows - this.rows);
        this.scrollRow = Math.max(0, Math.min(maxScroll, this.scrollRow));
    }

    private T itemAtSlot(int slot) {
        if (slot < 0 || slot >= this.columns * this.rows) {
            return null;
        }
        int index = this.scrollRow * this.columns + slot;
        return index < this.items.size() ? this.items.get(index) : null;
    }

    /** Slot number (row by row) under the point, or -1. */
    private int slotAt(int mouseX, int mouseY) {
        float dx = mouseX - this.x;
        float dy = mouseY - this.y;
        if (dx < 0 || dy < 0 || dx >= this.columns * this.slotSize || dy >= this.rows * this.slotSize) {
            return -1;
        }
        return ((int) dy / this.slotSize) * this.columns + ((int) dx / this.slotSize);
    }

    private int slotX(int slot) {
        return (int) this.x + (slot % this.columns) * this.slotSize;
    }

    private int slotY(int slot) {
        return (int) this.y + (slot / this.columns) * this.slotSize;
    }

    // ---------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------

    @Override
    public void measure(Font font) {
        super.measure(font);
        this.columns = Math.max(1, (int) (this.width / this.slotSize));
        this.rows = Math.max(1, (int) (this.height / this.slotSize));
        clampScroll();
    }

    // ---------------------------------------------------------------------------------------
    // Rendering / input
    // ---------------------------------------------------------------------------------------

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        int windowWidth = (int) getRootWidth();
        int windowHeight = (int) getRootHeight();
        int capacity = this.columns * this.rows;
        int hovered = slotAt(mouseX, mouseY);

        // Highlights first, so the icons sit on top of them.
        Gui2D.begin(windowWidth, windowHeight);
        for (int i = 0; i < capacity; i++) {
            T item = itemAtSlot(i);
            if (item == null) {
                break;
            }

            float sx = slotX(i);
            float sy = slotY(i);

            if (i == hovered) {
                Gui2D.fillRect(sx, sy, this.slotSize, this.slotSize, 1.0F, 1.0F, 1.0F, 0.18F);
            }
            if (this.selected.test(item)) {
                Gui2D.drawRectOutline(sx, sy, this.slotSize, this.slotSize, 1.0F, 1.0F, 0.0F, 1.0F);
                Gui2D.drawRectOutline(sx + 1, sy + 1, this.slotSize - 2, this.slotSize - 2, 1.0F, 1.0F, 0.0F, 1.0F);
            }
        }
        Gui2D.end();

        if (this.iconDrawer != null) {
            for (int i = 0; i < capacity; i++) {
                T item = itemAtSlot(i);
                if (item == null) {
                    break;
                }

                float cx = slotX(i) + this.slotSize / 2.0F;
                float cy = slotY(i) + this.slotSize / 2.0F - 1;
                this.iconDrawer.draw(item, cx, cy, this.slotSize * this.iconFraction, windowWidth, windowHeight);
            }
        }

        if (this.overlayDrawer != null) {
            Gui2D.begin(windowWidth, windowHeight);
            for (int i = 0; i < capacity; i++) {
                T item = itemAtSlot(i);
                if (item == null) {
                    break;
                }
                this.overlayDrawer.draw(font, item, slotX(i), slotY(i), this.slotSize);
            }
            Gui2D.end();
        }
    }

    @Override
    protected boolean isInteractive() {
        return true;
    }

    @Override
    public void onMouseClicked(int mouseX, int mouseY) {
        T item = itemAtSlot(slotAt(mouseX, mouseY));
        if (item != null) {
            this.onSelect.accept(item);
        }
    }
}