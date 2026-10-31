package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.render.Font;

/**
 * A texture drawn stretched over the widget's rectangle.
 * <pre>
 *     add(new Image("/assets/rubydung/textures/gui/logo.png", 498, 110), Anchor.TOP_CENTER, 0, 0);
 * </pre>
 */
public class Image extends Widget {

    private final String texturePath;

    public Image(String texturePath, float width, float height) {
        this.texturePath = texturePath;
        setSize(width, height);
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        Gui2D.drawTexture(this.texturePath, this.x, this.y, this.width, this.height);
    }
}
