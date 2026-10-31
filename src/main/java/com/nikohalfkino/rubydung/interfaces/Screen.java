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
 * Base class of every full-window screen. A screen is a tree of {@link Widget}s: build it in the
 * constructor with {@link #add}, and drawing, hover, clicks, focus, Tab and Escape all work
 * without any per-screen render/click code.
 * <pre>
 *     public class MyScreen extends MenuScreen {
 *         public MyScreen(RubyDung game) {
 *             super(game);
 *             addTitle("My Screen");
 *             Column menu = addColumn(10, UiStyle.CONTENT_Y);
 *             menu.add(new Button("Do it").onClick(() -> doIt()));
 *             menu.add(new Button("Back").onClick(() -> game.openScreen(new TitleScreen(game))));
 *             onEscape(() -> game.openScreen(new TitleScreen(game)));
 *         }
 *     }
 * </pre>
 * Screens with fully custom drawing (the inventory, the loading screen) can still override
 * {@link #render} and ignore the widget tree.
 */
public abstract class Screen {

    protected int width;
    protected int height;

    /** Used by every widget to draw text. Null only for screens built with the no-argument constructor. */
    protected Font font;

    /** Everything on the screen hangs off this group, which always fills {@link #layoutRoot()}'s rectangle. */
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

    public boolean pausesGame() {
        return true;
    }

    public void onClose() {
        if (this.keyRepeat) {
            Keyboard.enableRepeatEvents(false);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Building the screen
    // ---------------------------------------------------------------------------------------

    /** Adds a widget to the screen; position it with {@link Widget#setAnchor} first, or use the 4-argument overload. */
    protected <T extends Widget> T add(T widget) {
        return this.root.add(widget);
    }

    /** Adds a widget pinned to {@code anchor} of the window, nudged by (dx, dy) pixels. */
    protected <T extends Widget> T add(T widget, Anchor anchor, float dx, float dy) {
        return this.root.add(widget, anchor, dx, dy);
    }

    /** What Escape does when no text box is using it. */
    protected void onEscape(Runnable action) {
        this.escapeAction = action;
    }

    /** What Enter does when no text box is using it. */
    protected void onEnter(Runnable action) {
        this.enterAction = action;
    }

    /** True = this screen swallows every key it doesn't use, so nothing reaches the game behind it. */
    protected void setSwallowKeys(boolean swallowKeys) {
        this.swallowKeys = swallowKeys;
    }

    /** Typing a key keeps firing while it is held (for screens with text boxes). */
    protected void setKeyRepeat(boolean keyRepeat) {
        this.keyRepeat = keyRepeat;
    }

    /** When true, clicking something that isn't a text box does not take focus away from the focused one. */
    protected void setStickyFocus(boolean stickyFocus) {
        this.stickyFocus = stickyFocus;
    }

    /** Gives the keyboard to a text box (or null to clear). */
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

    // ---------------------------------------------------------------------------------------
    // Layout & drawing
    // ---------------------------------------------------------------------------------------

    /** Lays the widget tree out in the window. Screens drawn inside the game frame override this to use the panel's rectangle. */
    protected void layoutRoot() {
        this.root.layoutRoot(this.font, 0.0f, 0.0f, this.width, this.height);
    }

    /** Drawn first, behind all widgets (menu background, plain fill, ...). */
    protected void renderBackground() {
    }

    /** Drawn last, on top of all widgets. */
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

    // ---------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------

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

    /** Moves focus to the next visible text box (wrapping round). Does nothing unless one is focused. */
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
