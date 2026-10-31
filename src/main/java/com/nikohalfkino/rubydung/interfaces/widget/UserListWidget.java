package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.render.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An IRC-style user list: one "@nick" row per user, each nick in its own colour, optionally with
 * a "(You!)" tag after the local player's name (see {@link #setSelf}). Fills the rectangle it is
 * given and stops when it runs out of height. Names that are too wide are broken across lines, and
 * the "(You!)" tag moves to its own line when it does not fit beside the name. The user names come from a supplier, so the list
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

    private int textWidth(Font font, String text) {
        return (int) (font.getWidth(text) * this.scale);
    }

    /** Splits a name into pieces no wider than {@code limit} pixels (always at least one piece). */
    private List<String> breakName(Font font, String name, int limit) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (current.length() > 0 && textWidth(font, current.toString() + c) > limit) {
                pieces.add(current.toString());
                current.setLength(0);
            }
            current.append(c);
        }

        pieces.add(current.toString());
        return pieces;
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
        int prefixWidth = textWidth(font, "@");
        int nameX = px + PAD + prefixWidth;
        int available = Math.max(1, (int) this.width - PAD * 2 - prefixWidth);

        rows:
        for (String name : names) {
            String shown = name == null ? "" : name;
            int color = this.colorOf.apply(name);

            List<String> pieces = breakName(font, shown, available);

            for (int i = 0; i < pieces.size(); i++) {
                if (y + line > bottom) {
                    break rows;
                }
                if (i == 0) {
                    text(font, "@", px + PAD, y, OP_COLOR);
                }
                text(font, pieces.get(i), nameX, y, color);

                boolean last = i == pieces.size() - 1;
                if (last && shown.equals(selfName)) {
                    String lastPiece = pieces.get(i);
                    if (textWidth(font, lastPiece + SELF_TAG) <= available) {
                        // Fits beside the name.
                        text(font, SELF_TAG, nameX + textWidth(font, lastPiece), y, SELF_COLOR);
                    } else {
                        // Does not fit: put the tag on its own line, under the name.
                        y += line;
                        if (y + line > bottom) {
                            break rows;
                        }
                        text(font, SELF_TAG.trim(), nameX, y, SELF_COLOR);
                    }
                }

                y += line;
            }
        }

        Gui2D.end();
    }
}