package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.render.Font;

import java.util.function.Supplier;

/**
 * A line of text. Sized from the text unless given a fixed size, in which case the text is
 * centred inside that box (useful for click targets like a "Reset" link).
 * <pre>
 *     add(new Label("Options"), Anchor.TOP_CENTER, 0, UiStyle.TITLE_Y);
 *     add(new Label(() -> "Page " + page), Anchor.CENTER, 0, 0);          // text re-read every frame
 *     Label reset = new Label("Reset"); reset.setColor(0xFF8888); reset.setHoverColor(0xFFFF00);
 *     reset.onClick(() -> doReset());                                       // makes it clickable
 * </pre>
 */
public class Label extends Widget {

    private final Supplier<String> text;

    private int color = UiStyle.TEXT;
    private int hoverColor = -1;
    private float scale = 0.0f;
    private int shadowOffset = 0;
    private Supplier<Float> alpha;
    private Runnable onClick;

    public Label(String text) {
        this.text = () -> text;
    }

    /** A label whose text is looked up again every frame. */
    public Label(Supplier<String> text) {
        this.text = text;
    }

    public Label setColor(int color) {
        this.color = color;
        return this;
    }

    /** Colour while the mouse is over the label (-1 = no change). */
    public Label setHoverColor(int hoverColor) {
        this.hoverColor = hoverColor;
        return this;
    }

    /** Draws the text at this scale instead of the default size (0 = default). */
    public Label setScale(float scale) {
        this.scale = scale;
        return this;
    }

    /** Draws a black copy of the text this many pixels down-right behind it. */
    public Label setShadow(int offset) {
        this.shadowOffset = offset;
        return this;
    }

    /**
     * Opacity (0..1), re-read every frame, for fading text out. Only applies to default-size text;
     * text drawn with {@link #setScale} is always opaque.
     */
    public Label setAlpha(Supplier<Float> alpha) {
        this.alpha = alpha;
        return this;
    }

    public Label onClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    private String currentText() {
        String value = this.text.get();
        return value == null ? "" : value;
    }

    private float textWidth(Font font, String value) {
        return this.scale > 0.0f ? font.getWidth(value) * this.scale : font.width(value);
    }

    private float textHeight() {
        return this.scale > 0.0f ? 8.0f * this.scale : UiStyle.LINE_HEIGHT;
    }

    @Override
    protected float naturalWidth(Font font) {
        return textWidth(font, currentText());
    }

    @Override
    protected float naturalHeight(Font font) {
        return textHeight();
    }

    @Override
    protected boolean isInteractive() {
        return this.onClick != null;
    }

    @Override
    public void onMouseClicked(int mouseX, int mouseY) {
        if (this.onClick != null) {
            this.onClick.run();
        }
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        String value = currentText();
        if (value.isEmpty()) {
            return;
        }

        int drawX = (int) (this.x + (this.width - textWidth(font, value)) / 2.0f);
        int drawY = (int) (this.y + (this.height - textHeight()) / 2.0f);

        int drawColor = this.color;
        if (this.hoverColor >= 0 && isEnabled() && contains(mouseX, mouseY)) {
            drawColor = this.hoverColor;
        }

        if (this.shadowOffset != 0) {
            drawText(font, value, drawX + this.shadowOffset, drawY + this.shadowOffset, 0x000000);
        }
        drawText(font, value, drawX, drawY, drawColor);
    }

    private void drawText(Font font, String value, int drawX, int drawY, int drawColor) {
        if (this.scale > 0.0f) {
            font.drawStringScaled(value, drawX, drawY, drawColor, this.scale);
        } else if (this.alpha != null) {
            font.drawString(value, drawX, drawY, drawColor, this.alpha.get());
        } else {
            font.drawString(value, drawX, drawY, drawColor);
        }
    }
}