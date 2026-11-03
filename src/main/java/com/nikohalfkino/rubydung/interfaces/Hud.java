package com.nikohalfkino.rubydung.interfaces;

import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Group;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;
import com.nikohalfkino.rubydung.render.Font;

import static org.lwjgl.opengl.GL11.*;

/**
 * always-on in-world overlay (crosshair, selected tile, debug text...), same widget tree as a Screen so elements use an anchor + offset
 *
 * old-style HudElements that draw with absolute coordinates can still be registered
 */
public class Hud {

    private final Group root = new Group();

    /** for elements that draw themselves with absolute coordinates */
    public void register(HudElement element) {
        this.root.add(new LegacyElement(element));
    }

    /** keeps the widget's own anchor / offset */
    public <T extends Widget> T register(T widget) {
        return this.root.add(widget);
    }

    /** pinned to anchor of the window, nudged by (dx, dy) pixels */
    public <T extends Widget> T register(T widget, Anchor anchor, float dx, float dy) {
        return this.root.add(widget, anchor, dx, dy);
    }

    public void render(Font font, int width, int height) {
        this.root.layoutRoot(font, 0.0f, 0.0f, width, height);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        // No mouse in the HUD: pass coordinates that are never over anything.
        this.root.render(font, -1, -1);

        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    // gives an old-style element the whole window
    private static final class LegacyElement extends Widget {

        private final HudElement element;

        LegacyElement(HudElement element) {
            this.element = element;
        }

        @Override
        public void measure(Font font) {
            this.width = getRootWidth();
            this.height = getRootHeight();
        }

        @Override
        protected void draw(Font font, int mouseX, int mouseY) {
            this.element.render(font, (int) this.width, (int) this.height);
        }
    }
}
