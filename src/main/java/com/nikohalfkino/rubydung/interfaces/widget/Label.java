package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.render.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * text, sized from the text unless given a fixed size (then centred in that box, useful for click targets like a "Reset" link)
 *
 * a \n in the text starts a new line, setMaxWidth wraps long text
 */
public class Label extends Widget {

    private final Supplier<String> text;

    private int color = UiStyle.TEXT;
    private int hoverColor = -1;
    private float scale = 0.0f;
    private int shadowOffset = 0;
    private int maxWidth = 0;
    private Supplier<Float> alpha;
    private Runnable onClick;

    public Label(String text) {
        this.text = () -> text;
    }

    /** text is looked up again every frame */
    public Label(Supplier<String> text) {
        this.text = text;
    }

    public Label setColor(int color) {
        this.color = color;
        return this;
    }

    /** colour while hovered (-1 = no change) */
    public Label setHoverColor(int hoverColor) {
        this.hoverColor = hoverColor;
        return this;
    }

    /** text scale instead of the default size (0 = default) */
    public Label setScale(float scale) {
        this.scale = scale;
        return this;
    }

    /** black copy of the text this many pixels down-right behind it */
    public Label setShadow(int offset) {
        this.shadowOffset = offset;
        return this;
    }

    /** opacity 0..1, re-read every frame, only for default-size text (setScale text is always opaque) */
    public Label setAlpha(Supplier<Float> alpha) {
        this.alpha = alpha;
        return this;
    }

    /** wraps so no line is wider than this many pixels (0 = never). breaks at spaces, a too-long word is split. a fixed-size label should pass its own width */
    public Label setMaxWidth(int maxWidth) {
        this.maxWidth = Math.max(0, maxWidth);
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

    private float lineHeight() {
        return this.scale > 0.0f ? 9.0f * this.scale : UiStyle.LINE_HEIGHT;
    }

    // text split at \n and wrapped to the max width if one is set
    private List<String> lines(Font font, String value) {
        List<String> out = new ArrayList<>();
        for (String paragraph : value.split("\\r?\\n", -1)) {
            if (this.maxWidth <= 0) {
                out.add(paragraph);
            } else {
                wrap(font, paragraph, this.maxWidth, out);
            }
        }
        return out;
    }

    private void wrap(Font font, String paragraph, float limit, List<String> out) {
        StringBuilder current = new StringBuilder();

        for (String word : paragraph.split(" ", -1)) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (textWidth(font, candidate) <= limit) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }

            if (current.length() > 0) {
                out.add(current.toString());
                current.setLength(0);
            }

            // The word alone is wider than a line: split it by characters.
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (current.length() > 0 && textWidth(font, current.toString() + c) > limit) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                current.append(c);
            }
        }

        out.add(current.toString());
    }

    private float blockHeight(int lineCount) {
        return lineHeight() * (lineCount - 1) + textHeight();
    }

    @Override
    protected float naturalWidth(Font font) {
        float widest = 0.0f;
        for (String line : lines(font, currentText())) {
            widest = Math.max(widest, textWidth(font, line));
        }
        return widest;
    }

    @Override
    protected float naturalHeight(Font font) {
        return blockHeight(lines(font, currentText()).size());
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

        List<String> lines = lines(font, value);
        float top = this.y + (this.height - blockHeight(lines.size())) / 2.0f;

        int drawColor = this.color;
        if (this.hoverColor >= 0 && isEnabled() && contains(mouseX, mouseY)) {
            drawColor = this.hoverColor;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }

            int drawX = (int) (this.x + (this.width - textWidth(font, line)) / 2.0f);
            int drawY = (int) (top + i * lineHeight());

            if (this.shadowOffset != 0) {
                drawText(font, line, drawX + this.shadowOffset, drawY + this.shadowOffset, 0x000000);
            }
            drawText(font, line, drawX, drawY, drawColor);
        }
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