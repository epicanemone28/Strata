package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.render.Font;

import java.util.function.Supplier;

import static org.lwjgl.opengl.GL11.*;

/** horizontal bar filled from the left with a green gradient, the value (0..1) is read every frame */
public class ProgressBar extends Widget {

    private final Supplier<Float> progress;

    public ProgressBar(float width, float height, Supplier<Float> progress) {
        this.progress = progress;
        setSize(width, height);
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        Gui2D.fillRect(this.x, this.y, this.width, this.height, 0.2f, 0.2f, 0.2f, 1.0f);

        Float value = this.progress.get();
        float fill = this.width * Math.max(0.0f, Math.min(1.0f, value == null ? 0.0f : value));
        if (fill <= 0.0f) {
            return;
        }

        glDisable(GL_TEXTURE_2D);
        glBegin(GL_QUADS);
        glColor4f(0.1f, 0.9f, 0.2f, 1.0f);
        glVertex2f(this.x, this.y);
        glColor4f(0.1f, 0.9f, 0.2f, 1.0f);
        glVertex2f(this.x, this.y + this.height);
        glColor4f(0.0f, 0.2f, 0.05f, 1.0f);
        glVertex2f(this.x + fill, this.y + this.height);
        glColor4f(0.0f, 0.2f, 0.05f, 1.0f);
        glVertex2f(this.x + fill, this.y);
        glEnd();
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
