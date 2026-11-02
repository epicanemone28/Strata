package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.render.Font;
import org.lwjgl.input.Mouse;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A horizontal slider for picking a value in a range. Drawn as a 9-slice track ({@code
 * slider_track.png}, same 20x20/border-6 layout {@link Button} uses for {@code button.png})
 * with a thumb ({@code slider_thumb.png}, 10x16 source art, drawn 2x like everything else in
 * this UI) riding along it. The thumb stays fully on the track: its left edge is at the start
 * of the track at the minimum value and its right edge at the end at the maximum.
 * <p>
 * Dragging is self-polling: the slider checks {@link Mouse#isButtonDown} itself every frame while
 * it is being drawn, so screens need no drag callbacks.
 * <pre>
 *     column.add(new Slider("FOV", 300, 28, 30, 110, GameSettings.getFov(), true)
 *             .onChange(v -> GameSettings.setFov(v))      // live, while dragging
 *             .onCommit(GameSettings::save));              // once, when the drag ends with a new value
 * </pre>
 * By default the value is shown as a number; {@link #setFormatter} changes that (e.g. "Off" for 0).
 */
public class Slider extends Widget {

    private static final String TRACK_TEXTURE = "/assets/rubydung/textures/gui/slider_track.png";
    private static final String THUMB_TEXTURE = "/assets/rubydung/textures/gui/slider_thumb.png";

    private static final int TRACK_TEX_SIZE = 20;
    private static final int TRACK_BORDER = 6;

    /** slider_thumb.png is 10x16 pixels, drawn at THUMB_SCALE like the rest of the UI art. */
    private static final int THUMB_TEX_WIDTH = 10;
    private static final int THUMB_TEX_HEIGHT = 16;
    private static final float THUMB_SCALE = 2.0f;
    private static final float THUMB_WIDTH = THUMB_TEX_WIDTH * THUMB_SCALE;
    private static final float THUMB_HEIGHT = THUMB_TEX_HEIGHT * THUMB_SCALE;

    private static final int LABEL_HEIGHT = 16;

    private final String label;
    private final float minValue;
    private final float maxValue;

    /** Snap dragged/clicked values to whole numbers - for things like an integer FOV or FPS cap. */
    private final boolean wholeNumbers;

    private float value;
    private float valueAtPress;
    private boolean dragging = false;

    private Consumer<Float> onChange;
    private Runnable onCommit;
    private Function<Float, String> formatter;

    public Slider(String label, float width, float height, float minValue, float maxValue, float initialValue, boolean wholeNumbers) {
        this.label = label;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.wholeNumbers = wholeNumbers;
        this.value = clamp(initialValue);
        setSize(width, height);
    }

    /** Called with the new value every time dragging changes it. */
    public Slider onChange(Consumer<Float> onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Called once when a drag ends and the value is different from when it started. */
    public Slider onCommit(Runnable onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    /** How the value is written next to the label, e.g. {@code v -> v == 0 ? "Off" : v + " min"}. */
    public Slider setFormatter(Function<Float, String> formatter) {
        this.formatter = formatter;
        return this;
    }

    public float getValue() {
        return this.value;
    }

    public void setValue(float value) {
        this.value = clamp(value);
    }

    /** Whether a drag is currently in progress. */
    public boolean isDragging() {
        return this.dragging;
    }

    @Override
    protected boolean isInteractive() {
        return true;
    }

    /**
     * Starts a drag, immediately snapping the thumb under the cursor rather than waiting for the
     * first drag movement - clicking anywhere on the track jumps the value there.
     */
    @Override
    public void onMouseClicked(int mouseX, int mouseY) {
        mouseClicked(mouseX, mouseY);
    }

    public void mouseClicked(int mouseX, int mouseY) {
        if (isHovered(mouseX, mouseY)) {
            this.dragging = true;
            this.valueAtPress = this.value;
            setValueFromMouseX(mouseX);
        }
    }

    /**
     * Follows the mouse while a drag is in progress and ends it when the button comes back up.
     * Called automatically while drawing.
     */
    public void updateMouse(int mouseX, int mouseY) {
        if (!this.dragging) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            this.dragging = false;
            if (this.value != this.valueAtPress && this.onCommit != null) {
                this.onCommit.run();
            }
            return;
        }
        setValueFromMouseX(mouseX);
    }

    /** The thumb's centre travels from half a thumb inside the left end to half a thumb inside the right end. */
    private void setValueFromMouseX(int mouseX) {
        float usableWidth = this.width - THUMB_WIDTH;
        float t = usableWidth <= 0 ? 0 : (mouseX - (this.x + THUMB_WIDTH / 2.0f)) / usableWidth;
        t = Math.max(0.0f, Math.min(1.0f, t));

        float newValue = this.minValue + t * (this.maxValue - this.minValue);
        newValue = clamp(this.wholeNumbers ? Math.round(newValue) : newValue);

        if (newValue != this.value) {
            this.value = newValue;
            if (this.onChange != null) {
                this.onChange.accept(newValue);
            }
        }
    }

    private float clamp(float v) {
        return Math.max(this.minValue, Math.min(this.maxValue, v));
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        updateMouse(mouseX, mouseY);

        boolean enabled = isEnabled();

        Gui2D.drawNineSlice(TRACK_TEXTURE, this.x, this.y, this.width, this.height,
                TRACK_TEX_SIZE, TRACK_TEX_SIZE, TRACK_BORDER, enabled ? 1.0f : 0.35f);

        float range = this.maxValue - this.minValue;
        float t = range <= 0 ? 0.0f : (this.value - this.minValue) / range;
        float thumbX = this.x + t * (this.width - THUMB_WIDTH);
        float thumbY = this.y + (this.height - THUMB_HEIGHT) / 2.0f;
        Gui2D.drawTexture(THUMB_TEXTURE, thumbX, thumbY, THUMB_WIDTH, THUMB_HEIGHT);
        if (!enabled) {
            Gui2D.fillRect(thumbX, thumbY, THUMB_WIDTH, THUMB_HEIGHT, 0.0f, 0.0f, 0.0f, 0.65f);
        }

        String text = this.label + ": " + formatValue();
        int textWidth = font.width(text);
        int drawX = (int) (this.x + (this.width - textWidth) / 2.0f);
        int drawY = (int) (this.y + (this.height - LABEL_HEIGHT) / 2.0f);

        boolean hovered = enabled && isHovered(mouseX, mouseY);
        int color = !enabled ? UiStyle.TEXT_DISABLED
                : (hovered || this.dragging) ? UiStyle.TEXT_HOVER : UiStyle.TEXT;
        font.drawString(text, drawX, drawY, color);
    }

    private String formatValue() {
        if (this.formatter != null) {
            return this.formatter.apply(this.value);
        }
        return this.wholeNumbers ? String.valueOf(Math.round(this.value)) : String.format("%.2f", this.value);
    }
}
