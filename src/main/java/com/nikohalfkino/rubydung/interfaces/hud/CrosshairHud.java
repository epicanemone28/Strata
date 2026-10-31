package com.nikohalfkino.rubydung.interfaces.hud;

import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;
import com.nikohalfkino.rubydung.render.Font;

import static org.lwjgl.opengl.GL11.*;

public class CrosshairHud extends Widget {

    private static final float SIZE = 2.0f;

    public CrosshairHud() {
        setSize(SIZE * 2, SIZE * 2);
        setAnchor(Anchor.CENTER, 0, 0);
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        glDisable(GL_TEXTURE_2D);

        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE_MINUS_DST_COLOR, GL_ONE_MINUS_SRC_COLOR);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        glBegin(GL_QUADS);
        glVertex2f(this.x, this.y);
        glVertex2f(this.x + this.width, this.y);
        glVertex2f(this.x + this.width, this.y + this.height);
        glVertex2f(this.x, this.y + this.height);
        glEnd();

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_BLEND);
    }
}
