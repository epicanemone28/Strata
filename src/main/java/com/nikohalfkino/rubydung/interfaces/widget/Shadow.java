package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.render.Font;

/**
 * Draws the drop shadow of another widget (normally a {@link Panel} with its own shadow turned
 * off) on its own, so the shadow can sit in a different layer than the panel: add this first,
 * then whatever should cover the shadow, then the panel.
 */
public class Shadow extends Widget {

    private final Widget target;
    private final float alpha;

    public Shadow(Widget target, float alpha) {
        this.target = target;
        this.alpha = alpha;
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        if (!this.target.isVisible()) {
            return;
        }
        Gui2D.fillRect(this.target.getX() + Panel.SHADOW_OFFSET, this.target.getY() + Panel.SHADOW_OFFSET,
                this.target.getWidth(), this.target.getHeight(), 0.0f, 0.0f, 0.0f, this.alpha);
    }
}