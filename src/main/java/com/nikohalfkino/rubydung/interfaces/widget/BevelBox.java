package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Bevel;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.render.Font;

/**
 * sunken box with the frame's bevel (see Bevel), backdrop for an input line or text area
 *
 * the bevel is drawn just outside the rectangle (2 UI units per side), so leave room
 */
public class BevelBox extends Widget {

    private int tint = 0xFFFFFF;
    private float fillR = 0.0F;
    private float fillG = 0.0F;
    private float fillB = 0.0F;
    private float fillA = 0.55F;

    /** colour multiplier for the bevel, pass the screen's getFrameTint() to match the frame */
    public BevelBox setTint(int tint) {
        this.tint = tint;
        return this;
    }

    public BevelBox setFill(float r, float g, float b, float a) {
        this.fillR = r;
        this.fillG = g;
        this.fillB = b;
        this.fillA = a;
        return this;
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        Gui2D.begin((int) getRootWidth(), (int) getRootHeight());
        Gui2D.fillRect(this.x, this.y, this.width, this.height, this.fillR, this.fillG, this.fillB, this.fillA);
        Bevel.sunkenFrame(this.x, this.y, this.width, this.height, this.tint);
        Gui2D.end();
    }
}