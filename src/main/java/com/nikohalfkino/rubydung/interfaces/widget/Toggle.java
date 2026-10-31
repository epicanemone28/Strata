package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.render.Font;

import java.util.function.Consumer;

/**
 * An on/off switch drawn as one of two whole-sprite textures ({@code toggle_on.png} / {@code
 * toggle_off.png}, both native 16x16 - no 9-slice needed since, unlike a {@link Button}, a
 * toggle is never stretched to an arbitrary size) with a text label beside it.
 * <p>
 * Two looks:
 * <ul>
 *   <li>No fixed width: sprite and label sit side by side, as wide as they need to be.</li>
 *   <li>Fixed width ({@link #setSize} / {@link #setRowWidth}) with the label on the left: a
 *       settings-row look - label at the left edge, sprite at the right edge, a grey line between.</li>
 * </ul>
 * <pre>
 *     column.add(new Toggle("VSync", GameSettings.isVsyncEnabled(), Toggle.LabelSide.LEFT)
 *             .setRowWidth(200).onChange(on -> GameSettings.setVsyncEnabled(on)));
 * </pre>
 */
public class Toggle extends Widget {

    private static final String ON_TEXTURE = "/assets/rubydung/textures/gui/toggle_on.png";
    private static final String OFF_TEXTURE = "/assets/rubydung/textures/gui/toggle_off.png";
    private static final float SPRITE_SIZE = 32.0f;
    private static final float LABEL_GAP = 12.0f;
    private static final int LABEL_HEIGHT = 16;

    public enum LabelSide {
        LEFT, RIGHT
    }

    private final String label;
    private final LabelSide labelSide;

    private boolean value;
    private Consumer<Boolean> onChange;

    public Toggle(String label, boolean initialValue, LabelSide labelSide) {
        this.label = label;
        this.labelSide = labelSide;
        this.value = initialValue;
    }

    /** Switches to the fixed-width settings-row look (see class doc). */
    public Toggle setRowWidth(float rowWidth) {
        setSize(rowWidth, SPRITE_SIZE);
        return this;
    }

    /** Called with the new state after the toggle is clicked. */
    public Toggle onChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    public boolean getValue() {
        return this.value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    public float getLabelWidth(Font font) {
        return font.width(this.label);
    }

    public float getWidth(Font font) {
        return SPRITE_SIZE + LABEL_GAP + font.width(this.label);
    }

    @Override
    protected float naturalWidth(Font font) {
        return getWidth(font);
    }

    @Override
    protected float naturalHeight(Font font) {
        return SPRITE_SIZE;
    }

    private boolean isRowStyle() {
        return getPrefWidth() >= 0.0f && this.labelSide == LabelSide.LEFT;
    }

    @Override
    protected boolean isInteractive() {
        return true;
    }

    /** The hit area covers the sprite and the label both, so clicking the text also toggles it. */
    @Override
    public void onMouseClicked(int mouseX, int mouseY) {
        this.value = !this.value;
        if (this.onChange != null) {
            this.onChange.accept(this.value);
        }
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        float spriteX;
        float labelX;

        if (isRowStyle()) {
            // Text left-aligned at the start of the row, toggle button aligned to the right edge
            labelX = this.x;
            spriteX = this.x + this.width - SPRITE_SIZE;
        } else {
            spriteX = this.labelSide == LabelSide.LEFT
                    ? this.x + font.width(this.label) + LABEL_GAP
                    : this.x;
            labelX = this.labelSide == LabelSide.LEFT
                    ? this.x
                    : this.x + SPRITE_SIZE + LABEL_GAP;
        }

        String texture = this.value ? ON_TEXTURE : OFF_TEXTURE;
        Gui2D.drawTexture(texture, spriteX, this.y, SPRITE_SIZE, SPRITE_SIZE);

        // Draw a 2px thick grey horizontal line between the label and the toggle button
        if (isRowStyle()) {
            float lineStartX = labelX + font.width(this.label) + 6.0f;
            float lineEndX = spriteX - 6.0f;
            if (lineEndX > lineStartX) {
                float lineHeight = 2.0f;
                float lineY = this.y + (SPRITE_SIZE - lineHeight) / 2.0f;
                Gui2D.fillRect(lineStartX, lineY, lineEndX - lineStartX, lineHeight, 0.5f, 0.5f, 0.5f, 1.0f);
            }
        }

        boolean hovered = isEnabled() && isHovered(mouseX, mouseY);
        int color = hovered ? UiStyle.TEXT_HOVER : UiStyle.TEXT;
        int labelY = (int) (this.y + (SPRITE_SIZE - LABEL_HEIGHT) / 2.0f);
        font.drawString(this.label, (int) labelX, labelY, color);
    }
}
