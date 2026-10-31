package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.render.Font;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An IRC-style user list: one "@nick" row per user, each nick in its own colour, optionally with
 * a "(You!)" tag after the local player's name (see {@link #setSelf}). Fills the rectangle it is
 * given and stops when it runs out of height. The user names come from a supplier, so the list
 * is always current.
 */
public class UserListWidget extends Widget {

    private static final int PAD = 6;
    private static final int OP_COLOR = 0x55FF55;
    private static final int SELF_COLOR = 0x999999;
    private static final String SELF_TAG = " (You!)";

    private final Supplier<List<String>> users;
    private final Function<String, Integer> colorOf;
    private final float scale;
    private Supplier<String> self;

    public UserListWidget(Supplier<List<String>> users, Function<String, Integer> colorOf, float scale) {
        this.users = users;
        this.colorOf = colorOf;
        this.scale = scale;
    }

    /** Marks the user whose name {@code self} returns with a "(You!)" tag. */
    public UserListWidget setSelf(Supplier<String> self) {
        this.self = self;
        return this;
    }

    private int lineHeight() {
        return (int) (8 * this.scale) + 3;
    }

    private void text(Font font, String text, int x, int y, int color) {
        if (!text.isEmpty()) {
            font.drawStringScaled(text, x, y, color, this.scale);
        }
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        List<String> names = this.users.get();

        int px = (int) this.x;
        int py = (int) this.y;
        int bottom = py + (int) this.height;
        int line = lineHeight();

        String selfName = this.self == null ? null : this.self.get();

        Gui2D.begin((int) getRootWidth(), (int) getRootHeight());

        int y = py + PAD;
        int prefixWidth = (int) (font.getWidth("@") * this.scale);

        for (String name : names) {
            if (y + line > bottom) {
                break;
            }

            int nameX = px + PAD + prefixWidth;
            text(font, "@", px + PAD, y, OP_COLOR);
            text(font, name, nameX, y, this.colorOf.apply(name));

            if (name != null && name.equals(selfName)) {
                text(font, SELF_TAG, nameX + (int) (font.getWidth(name) * this.scale), y, SELF_COLOR);
            }

            y += line;
        }

        Gui2D.end();
    }
}