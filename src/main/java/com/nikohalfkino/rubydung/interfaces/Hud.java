package com.nikohalfkino.rubydung.interfaces;

import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Group;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;
import com.nikohalfkino.rubydung.render.Font;

import static org.lwjgl.opengl.GL11.*;

/**
 * The always-on in-world overlay (crosshair, selected tile, debug text, ...). It is the same kind
 * of widget tree a {@link Screen} uses, covering the whole window, so HUD elements are positioned
 * exactly like menu widgets - with an anchor and an offset.
 * <pre>
 *     hud.register(new CrosshairHud());                                   // uses the element's own default spot
 *     hud.register(new SelectedTileHud(game), Anchor.TOP_RIGHT, -10, 10); // ...or put it somewhere else
 * </pre>
 * Elements that predate the widget system (plain {@link HudElement}s that draw themselves with
 * absolute coordinates) can still be registered and are given the whole window.
 */
public class Hud {

    private final Group root = new Group();

    /** Registers an element that draws itself with absolute coordinates. */
    public void register(HudElement element) {
        this.root.add(new LegacyElement(element));
    }

    /** Registers a widget at the anchor / offset it already has. */
    public <T extends Widget> T register(T widget) {
        return this.root.add(widget);
    }

    /** Registers a widget pinned to {@code anchor} of the window, nudged by (dx, dy) pixels. */
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

    /** Adapter that gives an old-style element the whole window to draw in. */
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
