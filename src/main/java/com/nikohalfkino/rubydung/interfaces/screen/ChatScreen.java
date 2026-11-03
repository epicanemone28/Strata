package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.console.ChatLog;
import com.nikohalfkino.rubydung.interfaces.FrameMouthProvider;
import com.nikohalfkino.rubydung.interfaces.FramePanelScreen;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.widget.PanelSection;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.BevelBox;
import com.nikohalfkino.rubydung.interfaces.widget.TextField;
import com.nikohalfkino.rubydung.interfaces.widget.UserList;
import com.nikohalfkino.rubydung.render.Font;
import com.nikohalfkino.rubydung.util.GameSettings;
import com.nikohalfkino.rubydung.util.KeyBinding;
import com.nikohalfkino.rubydung.util.KeyMap;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.nikohalfkino.rubydung.RubyDung.GAME_TITLE;

public class ChatScreen extends FramePanelScreen implements FrameMouthProvider {

    // scale of every string this screen draws, the one knob for text size
    private static final float CHAT_SCALE = 2.0F;
    private static final int CHAR_HEIGHT = (int) (8 * CHAT_SCALE);
    private static final int LINE_HEIGHT = CHAR_HEIGHT + 3;
    private static final int PAD = 6;
    private static final int TOPIC_HEIGHT = CHAR_HEIGHT + 7;

    private static final int INPUT_Y_OFFSET = 24;
    private static final int MAX_INPUT = 8192;

    private static final boolean CLOSE_ON_SEND = false;

    private static final long OPEN_GRACE_NANOS = 150_000_000L;

    // between the input box's inside edge and its text
    private static final int INPUT_BOX_PADDING = 3;

    private static final int TEXT_COLOR = 0xE8E8E8;
    private static final int NOTICE_MARK_COLOR = 0x55CCFF;
    private static final int NOTICE_TEXT_COLOR = 0xAADDFF;

    private static final int[] NICK_COLORS = {
            0xFF7777, 0x77FF77, 0x7799FF, 0xFFFF66,
            0xFF77FF, 0x66FFFF, 0xFFAA55, 0xBB99FF
    };

    private final ChatLog chat;
    private final TextField input = new TextField(MAX_INPUT);
    private final long openedAt = System.nanoTime();

    // sunken backdrop of the input line (the text field is drawn on top)
    private final BevelBox inputBox;

    // right-hand user list, fills the panel section
    private final UserList userList;

    private int scrollLines = 0;
    private int historyIndex = -1;

    public ChatScreen(RubyDung game) {
        super(game);
        this.chat = game.getChatLog();
        this.chat.setNick(GameSettings.getUsername());
        Keyboard.enableRepeatEvents(true);

        this.inputBox = add(new BevelBox()
                .setTint(getFrameTint())
                .setFill(0.0F, 0.0F, 0.0F, 0.55F), Anchor.TOP_LEFT, 0, 0);
        this.inputBox.setVisibleWhen(this::isFrameOpen);

        this.userList = add(new UserList(
                () -> Collections.singletonList(this.chat.getNick()),
                ChatScreen::nickColor,
                CHAT_SCALE).setSelf(this.chat::getNick), Anchor.TOP_LEFT, 0, 0);
        this.userList.setVisibleWhen(this::isFrameOpen);

        addCornerButton("Done", this.game::closeScreen);
    }

    @Override
    protected void placeWidgets() {
        place(this.inputBox, this.layout.viewX, boxY(), this.layout.viewWidth, boxHeight());
        placeInSection(this.userList, 0);
    }

    @Override
    public void onClose() {
        Keyboard.enableRepeatEvents(false);
    }

    public void scroll(int lines) {
        this.scrollLines = Math.max(0, this.scrollLines + lines);
    }

    /** 0 = idle, 1..5 = A I U E O */
    @Override
    public int getMouthFrame() {
        return this.game.getSpeaker().getViseme().ordinal();
    }

    @Override
    public boolean showsSlots() {
        return false;
    }

    @Override
    public String getPanelTitle() {
        return "Chat";
    }

    @Override
    public int getFrameTint() {
        return 0xA8FFC0;
    }

    /** one plain panel under the portrait that fills down to the bottom (the user list) */
    @Override
    public List<PanelSection> getSections() {
        return Collections.singletonList(PanelSection.fill(getPanelTitle()));
    }

    @Override
    public boolean keyPressed(int key, char character) {
        if (key == KeyBinding.CHAT.getKey()
                && System.nanoTime() - this.openedAt < OPEN_GRACE_NANOS) {
            return true;
        }

        if (key == KeyMap.ESC) {
            this.game.closeScreen();
            return true;
        }

        if (key == KeyMap.ENTER || key == KeyMap.NUMPAD_ENTER) {
            submit();
            return true;
        }

        if (key == KeyMap.UP) {
            navigateHistory(1);
            return true;
        }

        if (key == KeyMap.DOWN) {
            navigateHistory(-1);
            return true;
        }

        if (key == Keyboard.KEY_PRIOR) {
            scroll(Math.max(1, visibleLineCapacity() / 2));
            return true;
        }

        if (key == Keyboard.KEY_NEXT) {
            scroll(-Math.max(1, visibleLineCapacity() / 2));
            return true;
        }

        // Let TextField handle input without restricting it to the visible width.
        this.input.keyTyped(key, character);
        return true;
    }

    private void navigateHistory(int direction) {
        List<String> history = this.chat.getHistory();

        if (history.isEmpty()) {
            return;
        }

        this.historyIndex = Math.max(
                -1,
                Math.min(history.size() - 1, this.historyIndex + direction)
        );

        if (this.historyIndex >= 0) {
            this.input.setText(history.get(history.size() - 1 - this.historyIndex));
        } else {
            this.input.clear();
        }
    }

    private void submit() {
        String text = this.input.getText().trim();

        this.input.clear();
        this.historyIndex = -1;
        this.scrollLines = 0;

        if (text.isEmpty()) {
            return;
        }

        this.chat.remember(text);

        if (text.startsWith(".")) {
            runCommand(text.substring(1).trim());
        } else {
            this.chat.say(text);
            this.game.getSpeaker().speak(text);
        }

        if (CLOSE_ON_SEND) {
            this.game.closeScreen();
        }
    }

    private void runCommand(String line) {
        if (line.isEmpty()) {
            this.chat.notice("Type .help for a list of commands.");
            return;
        }

        if (line.split("\\s+")[0].equalsIgnoreCase("clear")) {
            this.chat.clear();
            return;
        }

        for (String output : this.game.getConsole().execute(line)) {
            this.chat.notice(output);
        }
    }

    /** conversation and input text, drawn after the widgets so it's on top of the input box */
    @Override
    protected void renderForeground(int mouseX, int mouseY) {
        if (!isFrameOpen()) {
            return;
        }

        Font font = this.game.getFont();

        Gui2D.begin(this.game.getWidth(), this.game.getHeight());

        drawConversation(font);
        drawInputLine(font, mouseX, mouseY);

        Gui2D.end();
    }

    private int visibleLineCapacity() {
        if (this.layout == null) {
            return 1;
        }

        return Math.max(
                1,
                (this.layout.viewHeight - TOPIC_HEIGHT - PAD * 2) / LINE_HEIGHT
        );
    }

    private static final class VisualLine {
        final ChatLog.Line source;
        final boolean first;
        final String text;

        VisualLine(ChatLog.Line source, boolean first, String text) {
            this.source = source;
            this.first = first;
            this.text = text;
        }
    }

    private void drawConversation(Font font) {
        int x = this.layout.viewX;
        int y = this.layout.viewY;
        int w = this.layout.viewWidth;
        int h = this.layout.viewHeight;

        Gui2D.fillRect(x, y, w, h, 0.0F, 0.0F, 0.0F, 0.55F);

        Gui2D.fillRect(
                x, y, w, TOPIC_HEIGHT,
                0.10F, 0.15F, 0.45F, 0.9F
        );

        String topic = "Topic for " + this.chat.getChannel()
                + ": Welcome to " + GAME_TITLE
                + (this.scrollLines > 0 ? "  [scrollback]" : "");

        drawText(
                font,
                fit(font, topic, w - PAD * 2),
                x + PAD,
                y + 4,
                0xFFFFFF
        );

        int capacity = visibleLineCapacity();
        int textWidth = w - PAD * 2;

        List<ChatLog.Line> all = this.chat.getLines();
        List<VisualLine> visual = new ArrayList<>();

        int needed = capacity + this.scrollLines;

        for (int i = all.size() - 1; i >= 0 && visual.size() < needed; i--) {
            ChatLog.Line line = all.get(i);

            int prefixWidth = textWidth(font, markOf(line));

            List<String> pieces = wrap(
                    font,
                    line.text,
                    textWidth - prefixWidth,
                    textWidth
            );

            for (int p = pieces.size() - 1; p >= 0; p--) {
                visual.add(new VisualLine(line, p == 0, pieces.get(p)));
            }
        }

        this.scrollLines = Math.min(
                this.scrollLines,
                Math.max(0, visual.size() - capacity)
        );

        int baseY = y + h - PAD - LINE_HEIGHT;
        int end = Math.min(visual.size(), this.scrollLines + capacity);

        for (int i = this.scrollLines; i < end; i++) {
            VisualLine v = visual.get(i);

            int lineY = baseY - (i - this.scrollLines) * LINE_HEIGHT;
            int lineX = x + PAD;

            if (!v.first) {
                drawText(font, v.text, lineX, lineY, textColorOf(v.source));
                continue;
            }

            int markX = lineX;

            String mark = markOf(v.source);

            int markColor = v.source.kind == ChatLog.Kind.MESSAGE
                    ? nickColor(v.source.nick)
                    : NOTICE_MARK_COLOR;

            drawText(font, mark, markX, lineY, markColor);

            drawText(
                    font,
                    v.text,
                    markX + textWidth(font, mark),
                    lineY,
                    textColorOf(v.source)
            );
        }
    }

    private int inputX() {
        return this.layout.viewX + PAD;
    }

    private int inputY() {
        return this.layout.viewY + this.layout.viewHeight + INPUT_Y_OFFSET;
    }

    // top of the input box, as wide as the world view above it
    private int boxY() {
        return inputY() - INPUT_BOX_PADDING;
    }

    private int boxHeight() {
        return CHAR_HEIGHT + INPUT_BOX_PADDING * 2;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY) {
        if (!isFrameOpen()) {
            return;
        }

        int x = inputX();
        int y = inputY();

        boolean onInput = mouseX >= this.layout.viewX
                && mouseX < this.layout.viewX + this.layout.viewWidth
                && mouseY >= boxY()
                && mouseY < boxY() + boxHeight();

        if (onInput) {
            this.input.mouseClicked(
                    mouseX,
                    mouseY,
                    x,
                    y,
                    inputWidth(),
                    this.game.getFont(),
                    prompt()
            );
            return;
        }

        // Anything else: the widgets (the Done button).
        super.mouseClicked(mouseX, mouseY);
    }

    private void drawInputLine(Font font, int mouseX, int mouseY) {
        int x     = inputX();
        int y     = inputY();
        int width = inputWidth();

        this.input.updateMouse(mouseX, mouseY, x, y, width, font, prompt());
        this.input.render(font, x, y, 0xFFFFFF, prompt(), width);
    }

    private String prompt() {
        return "> ";
    }

    private int inputWidth() {
        return this.layout.viewWidth - PAD * 2;
    }

    private static String markOf(ChatLog.Line line) {
        return line.kind == ChatLog.Kind.MESSAGE
                ? "<" + line.nick + "> "
                : "*** ";
    }

    private static int textColorOf(ChatLog.Line line) {
        return line.kind == ChatLog.Kind.MESSAGE
                ? TEXT_COLOR
                : NOTICE_TEXT_COLOR;
    }

    private static int nickColor(String nick) {
        return NICK_COLORS[
                Math.floorMod(
                        nick == null ? 0 : nick.hashCode(),
                        NICK_COLORS.length
                )
                ];
    }

    private static int textWidth(Font font, String text) {
        return (int) (font.getWidth(text) * CHAT_SCALE);
    }

    private void drawText(Font font, String text, int x, int y, int color) {
        if (!text.isEmpty()) {
            font.drawStringScaled(text, x, y, color, CHAT_SCALE);
        }
    }

    private static String fit(Font font, String text, int maxWidth) {
        int end = text.length();

        while (end > 0 && textWidth(font, text.substring(0, end)) > maxWidth) {
            end--;
        }

        return text.substring(0, end);
    }

    private static List<String> wrap(Font font, String text, int firstWidth, int restWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        int limit = Math.max(1, firstWidth);

        for (String word : text.split(" ", -1)) {
            String candidate = current.length() == 0
                    ? word
                    : current + " " + word;

            if (textWidth(font, candidate) <= limit) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }

            if (current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                limit = Math.max(1, restWidth);
            }

            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);

                if (current.length() > 0
                        && textWidth(font, current.toString() + c) > limit) {
                    lines.add(current.toString());
                    current.setLength(0);
                    limit = Math.max(1, restWidth);
                }

                current.append(c);
            }
        }

        lines.add(current.toString());

        return lines;
    }
}