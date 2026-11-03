package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.render.Font;

/** a texture stretched over the widget's rectangle */
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
