package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Bevel;
import com.nikohalfkino.rubydung.interfaces.GameFrame;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.render.Font;

import java.util.function.Supplier;

/**
 * A clickable button. Give it its action with {@link #onClick}; there is no click handling to
 * write in the screen.
 * <pre>
 *     column.add(new Button("Play", 200, 30, true).onClick(() -> game.openScreen(...)));
 *     Button cap = column.add(new Button(() -> "FPS Cap: " + cap(), 200, 30, true));
 *     cap.setEnabledWhen(() -> !vsyncOn);      // greyed out and unclickable while the condition fails
 * </pre>
 */
public class Button extends Widget {

    private static final String DEFAULT_9SLICE_TEXTURE = "/assets/rubydung/textures/gui/button.png";
    private static final int TEXT_HEIGHT = 8;

    private Supplier<String> label;
    private Runnable onClick;
    private boolean selected = false;

    /** Whether the cursor was over the (enabled) button last frame, so the hover sound plays once per entry. */
    private boolean wasHovered = false;

    private boolean useNineSlice = false;
    private boolean beveled = false;
    private String texturePath = DEFAULT_9SLICE_TEXTURE;

    public Button(String label, float width, float height) {
        this(label, width, height, false);
    }

    public Button(String label, float width, float height, boolean useNineSlice) {
        setLabel(label);
        setSize(width, height);
        this.useNineSlice = useNineSlice;
    }

    /** Standard menu button (see {@link UiStyle}). */
    public Button(String label) {
        this(label, UiStyle.BUTTON_WIDTH, UiStyle.BUTTON_HEIGHT, true);
    }

    /** A button whose label is looked up again every frame. */
    public Button(Supplier<String> label, float width, float height, boolean useNineSlice) {
        this.label = label;
        setSize(width, height);
        this.useNineSlice = useNineSlice;
    }

    /** What happens when the button is clicked. Not called while the button is disabled. */
    public Button onClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    public void setLabel(String label) {
        this.label = () -> label;
    }

    public void setLabel(Supplier<String> label) {
        this.label = label;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return this.selected;
    }

    /** Draws the button as a raised bevelled slab (see {@link Bevel#raisedFrame}) instead of the texture. */
    public Button beveled() {
        this.beveled = true;
        return this;
    }

    public void setUseNineSlice(boolean useNineSlice) {
        this.useNineSlice = useNineSlice;
    }

    @Override
    protected boolean isInteractive() {
        return true;
    }

    @Override
    public void onMouseClicked(int mouseX, int mouseY) {
        if (isEnabled()) {
            UiSounds.click();
        }
        if (this.onClick != null) {
            this.onClick.run();
        }
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        boolean enabled = isEnabled();
        boolean hovered = enabled && isHovered(mouseX, mouseY);

        if (hovered && !this.wasHovered) {
            UiSounds.hover();
        }
        this.wasHovered = hovered;

        // Dims the texture/background when disabled. Doesn't affect the outline below, which is
        // its own always-full-strength draw either way, since a disabled button is never meant to
        // look "selected" in the first place.
        float buttonBrightness = enabled ? 1.0f : 0.35f;

        if (this.beveled) {
            drawBeveled(enabled, hovered);
        } else if (this.useNineSlice) {
            Gui2D.drawNineSlice(this.texturePath, this.x, this.y, this.width, this.height, 20, 20, 6, buttonBrightness);

            if (this.selected) {
                // 2px thick outline only on the bottom face
                Gui2D.fillRect(this.x, this.y + this.height - 2.0f, this.width, 2.0f, 1.0f, 1.0f, 0.4f, 1.0f);
            }
        } else {
            float alpha = this.selected ? 0.55f : (hovered ? 0.45f : 0.3f);
            if (!enabled) {
                // This path is a flat black fill rather than a texture, so there's no RGB to
                // darken - fall back to fading it out instead.
                alpha *= 0.5f;
            }
            Gui2D.fillRect(this.x, this.y, this.width, this.height, 0.0f, 0.0f, 0.0f, alpha);

            if (this.selected) {
                Gui2D.fillRect(this.x, this.y + this.height - 2.0f, this.width, 2.0f, 1.0f, 1.0f, 0.4f, 1.0f);
            }
        }

        String text = this.label.get();
        if (text == null) {
            text = "";
        }

        int textWidth = font.width(text);
        int scaledTextHeight = TEXT_HEIGHT * 2;

        int drawX = (int) (this.x + (this.width - textWidth) / 2.0f);
        int drawY = (int) (this.y + (this.height - scaledTextHeight) / 2.0f);

        int color;
        if (!enabled) {
            color = UiStyle.TEXT_DISABLED; // dimmed grey, distinct from the normal white/hover-yellow
        } else {
            color = (hovered || this.selected) ? UiStyle.TEXT_HOVER : UiStyle.TEXT;
        }
        font.drawString(text, drawX, drawY, color);
    }

    private void drawBeveled(boolean enabled, boolean hovered) {
        float u = GameFrame.UI_SCALE;

        float base = !enabled ? 0.16f : (hovered ? 0.36f : 0.27f);
        Gui2D.fillRect(this.x, this.y, this.width, this.height, base, base, base + 0.07f, 1.0f);

        float t = enabled ? 1.0f : 0.5f;
        Bevel.raisedFrame(this.x, this.y, this.width, this.height, t, t, t);

        if (this.selected) {
            Gui2D.fillRect(this.x + 2 * u, this.y + this.height - 3 * u, this.width - 4 * u, u, 1.0f, 1.0f, 0.4f, 1.0f);
        }
    }
}
