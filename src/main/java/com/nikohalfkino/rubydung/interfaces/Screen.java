package com.nikohalfkino.rubydung.interfaces;

import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Group;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;
import com.nikohalfkino.rubydung.render.Font;
import com.nikohalfkino.rubydung.util.KeyMap;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * base class of every full-window screen: a tree of Widgets built with add() in the constructor, drawing / hover / clicks / focus / Tab / Escape just work
 *
 * screens with fully custom drawing (inventory, loading) can override render and ignore the tree
 */
public abstract class Screen {

    protected int width;
    protected int height;

    // used by every widget to draw text, null only for screens built with the no-arg constructor
    protected Font font;

    // everything hangs off this, it fills layoutRoot()'s rectangle
    protected final Group root = new Group();

    private Widget focused;
    private boolean stickyFocus = false;
    private boolean swallowKeys = false;
    private boolean keyRepeat = false;
    private Runnable escapeAction;
    private Runnable enterAction;

    protected Screen() {
    }

    protected Screen(Font font) {
        this.font = font;
    }

    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        if (this.keyRepeat) {
            Keyboard.enableRepeatEvents(true);
        }
    }

    /** logical window size changed (fullscreen / mode switch), layout is redone every frame anyway */
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public boolean pausesGame() {
        return true;
    }

    public void onClose() {
        if (this.keyRepeat) {
            Keyboard.enableRepeatEvents(false);
        }
    }

    // building the screen

    /** position with Widget.setAnchor first, or use the 4-arg overload */
    protected <T extends Widget> T add(T widget) {
        return this.root.add(widget);
    }

    /** pinned to anchor of the window, nudged by (dx, dy) pixels */
    protected <T extends Widget> T add(T widget, Anchor anchor, float dx, float dy) {
        return this.root.add(widget, anchor, dx, dy);
    }

    /** what Escape does when no text box is using it */
    protected void onEscape(Runnable action) {
        this.escapeAction = action;
    }

    /** what Enter does when no text box is using it */
    protected void onEnter(Runnable action) {
        this.enterAction = action;
    }

    /** true = swallows every key it doesn't use so nothing reaches the game behind */
    protected void setSwallowKeys(boolean swallowKeys) {
        this.swallowKeys = swallowKeys;
    }

    /** keys keep firing while held (for text boxes) */
    protected void setKeyRepeat(boolean keyRepeat) {
        this.keyRepeat = keyRepeat;
    }

    /** true = clicking something that isn't a text box doesn't take focus from the focused one */
    protected void setStickyFocus(boolean stickyFocus) {
        this.stickyFocus = stickyFocus;
    }

    /** gives the keyboard to a text box, null clears */
    protected void setFocus(Widget widget) {
        if (this.focused == widget) {
            return;
        }
        Widget previous = this.focused;
        this.focused = widget;
        if (previous != null) {
            previous.setFocused(false);
        }
        if (widget != null) {
            widget.setFocused(true);
        }
    }

    protected Widget getFocus() {
        return this.focused;
    }

    // layout & drawing

    /** screens drawn inside the game frame override this to use the panel's rectangle */
    protected void layoutRoot() {
        this.root.layoutRoot(this.font, 0.0f, 0.0f, this.width, this.height);
    }

    /** drawn first, behind the widgets */
    protected void renderBackground() {
    }

    /** drawn last, on top of the widgets */
    protected void renderForeground(int mouseX, int mouseY) {
    }

    public void render(int mouseX, int mouseY) {
        if (this.font == null) {
            return;
        }

        if (this.focused != null && !this.focused.isVisible()) {
            setFocus(null);
        }

        layoutRoot();

        Gui2D.begin(this.width, this.height);
        renderBackground();
        this.root.render(this.font, mouseX, mouseY);
        renderForeground(mouseX, mouseY);
        Gui2D.end();
    }

    // input

    public void mouseClicked(int mouseX, int mouseY) {
        Widget hit = this.root.pick(mouseX, mouseY);

        if (hit != null && hit.isFocusable()) {
            setFocus(hit);
        } else if (!this.stickyFocus) {
            setFocus(null);
        }

        if (hit != null) {
            hit.onMouseClicked(mouseX, mouseY);
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            mouseClicked(mouseX, mouseY);
        }
    }

    public boolean keyPressed(int key, char character) {
        if (this.focused != null) {
            boolean handled = this.focused.keyPressed(key, character);
            if (this.focused != null && this.focused.takeBlurRequest()) {
                setFocus(null);
            }
            if (handled) {
                return true;
            }
        }

        if (key == KeyMap.TAB && focusNext()) {
            return true;
        }

        if (key == KeyMap.ESC && this.escapeAction != null) {
            this.escapeAction.run();
            return true;
        }

        if ((key == KeyMap.ENTER || key == KeyMap.NUMPAD_ENTER) && this.enterAction != null) {
            this.enterAction.run();
            return true;
        }

        return this.swallowKeys;
    }

    // next visible text box (wraps), does nothing unless one is focused
    private boolean focusNext() {
        if (this.focused == null) {
            return false;
        }

        List<Widget> focusable = new ArrayList<>();
        this.root.collectFocusable(focusable);
        if (focusable.size() < 2) {
            return false;
        }

        int index = focusable.indexOf(this.focused);
        setFocus(focusable.get((index + 1) % focusable.size()));
        return true;
    }
}
