package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.Bevel;
import com.nikohalfkino.rubydung.interfaces.DoorIcons;
import com.nikohalfkino.rubydung.interfaces.FramePanelScreen;
import com.nikohalfkino.rubydung.interfaces.FramePatternProvider;
import com.nikohalfkino.rubydung.interfaces.GameFrame;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.NoisePattern;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Label;
import com.nikohalfkino.rubydung.interfaces.widget.PanelSection;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;
import com.nikohalfkino.rubydung.level.DoorRegistry;
import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.level.tiles.DimensionalDoorTile;
import com.nikohalfkino.rubydung.level.tiles.DoorColor;
import com.nikohalfkino.rubydung.level.tiles.Tile;
import com.nikohalfkino.rubydung.level.WorldManager;
import com.nikohalfkino.rubydung.render.Font;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Menu opened by pressing the interact key on a dimensional door. The world view shows the world
 * list, drawn like {@link WorldListScreen} (same rows, same empty slots), with each world's doors
 * overlaid on the right of its row. The right-hand panel has the door colours you can pick, and the
 * whole frame is tinted with the colour of the door the menu was opened from.
 * <p>
 * Rules: a coloured door only leads to worlds that have a door of the same colour; regular doors
 * are universal (a regular door can pick any colour, and a regular door in the other world matches
 * any colour). A coloured door can only pick its own colour. Worlds with no matching door are dimmed
 * and can't be selected. The door stands open while the menu is up and closes again on dismissal.
 * <p>
 * Clicking a matching world travels there: the current world is saved and the other one loaded, and
 * the player arrives in front of a matching door (see RubyDung#travelThroughDoor and DoorTravel).
 * That door leads back, because the world you left has a matching door too.
 */
public class DimensionalDoorScreen extends FramePanelScreen implements FramePatternProvider {

    // Same row metrics and look as WorldListScreen.
    private static final int ROW_WIDTH = (int) UiStyle.PANEL_WIDTH;
    private static final int ROW_HEIGHT = 36;
    private static final int ROW_GAP = 4;
    private static final int TOTAL_WORLD_SLOTS = 10;
    private static final int TITLE_Y = 15;
    private static final int LIST_TOP = 45;

    /** Distance from the bottom of the world view to the world name in the bar below it. */
    private static final int BOTTOM_BAR_Y_OFFSET = 20;

    /** Text inside a row, measured from the row's left / top edge. */
    private static final int TEXT_X = 10;
    private static final int TEXT_Y = 10;

    /** Door badges: 14px of art at the UI's 2x scale, right-aligned in the row like the world list's. */
    private static final int DOOR_ICON_SIZE = 14 * GameFrame.UI_SCALE;
    private static final int DOOR_ICON_GAP = 4;
    private static final int BADGE_MARGIN = 4;
    private static final int NAME_BADGE_GAP = 8;
    private static final float DIMMED = 0.3F;

    // Sunken slot bevel greys, the same as the world list's rows and the inventory slots.
    private static final int SLOT_SHADOW = 12;
    private static final int SLOT_LIGHT = 112;

    private static final int SWATCH_SIZE = 34;
    private static final int SWATCH_GAP = 6;
    private static final int SWATCH_COLUMNS = 3;

    private static final class WorldEntry {
        final String name;
        final long lastPlayed;
        final Set<DoorColor> doors;

        WorldEntry(String name, long lastPlayed, Set<DoorColor> doors) {
            this.name = name;
            this.lastPlayed = lastPlayed;
            this.doors = doors;
        }
    }

    private final Level level;
    private final int doorX;
    private final int doorY;
    private final int doorZ;

    /** Colour of the door this menu was opened from. */
    private final DoorColor doorColor;

    /** Every saved world except the one the door is in. */
    private final List<WorldEntry> worlds = new ArrayList<>();

    private final Column panel;
    private final Label worldName;

    /** The colour being looked for in other worlds (REGULAR = any door at all). */
    private DoorColor filter;

    private int selected = -1;
    private int hovered = -1;
    private int scrollRows = 0;

    /** (doorX, doorY, doorZ) is the door's origin cell, see PlayerInteraction#getTargetedDoorOrigin. */
    public DimensionalDoorScreen(RubyDung game, int doorX, int doorY, int doorZ) {
        super(game);

        this.level = game.getLevel();
        this.doorX = doorX;
        this.doorY = doorY;
        this.doorZ = doorZ;

        Tile tile = Tile.byId(this.level.getTile(doorX, doorY, doorZ));
        this.doorColor = tile instanceof DimensionalDoorTile ? ((DimensionalDoorTile) tile).getColor() : DoorColor.REGULAR;
        this.filter = this.doorColor;

        onEscape(this.game::closeScreen);

        DimensionalDoorTile.setOpen(this.level, doorX, doorY, doorZ, true);

        for (WorldManager.WorldInfo world : WorldManager.listWorlds()) {
            if (!world.name.equals(this.level.getWorldName())) {
                this.worlds.add(new WorldEntry(world.name, world.lastPlayed, DoorRegistry.colors(world.name)));
            }
        }

        // The current world's name in the bar under the world view, once the frame has finished opening.
        this.worldName = add(new Label("Current World: \"" + this.level.getWorldName() + "\"")
                .setColor(0xFFFFFF), Anchor.TOP_LEFT, 0, 0);
        this.worldName.setVisibleWhen(this::isFrameOpen);

        this.panel = add(new Column(6.0f), Anchor.TOP_LEFT, 0, 0);
        this.panel.add(new Label("Door colour").setColor(UiStyle.TEXT_DIM));
        this.panel.add(new ColorPicker());

        addCornerButton("Done", this.game::closeScreen);
    }

    @Override
    public void onClose() {
        DimensionalDoorTile.setOpen(this.level, this.doorX, this.doorY, this.doorZ, false);
    }

    /** Scrolls the list by whole rows (positive = down). */
    public void scroll(int rows) {
        this.scrollRows = Math.max(0, Math.min(maxScroll(), this.scrollRows + rows));
    }

    @Override
    public boolean showsSlots() {
        return false;
    }

    @Override
    public String getPanelTitle() {
        return "Dimensional Door";
    }

    @Override
    public int getFrameTint() {
        // The frame takes the colour of the door; null only if the base class asks before the constructor has set it.
        return (this.doorColor == null ? DoorColor.REGULAR : this.doorColor).getTint();
    }

    /** Wooden frame, like the door itself. */
    @Override
    public NoisePattern getFramePattern() {
        return NoisePattern.WOOD_GRAIN;
    }

    @Override
    public List<PanelSection> getSections() {
        return Collections.singletonList(PanelSection.fill(getPanelTitle()));
    }

    @Override
    protected void placeWidgets() {
        placeCenteredInSection(this.panel, 0);

        // Centred in the bar under the world view (same spot PauseScreen uses for "Level Saved!").
        this.worldName.measure(this.font);
        this.worldName.setAnchor(Anchor.TOP_LEFT,
                this.layout.viewX + (this.layout.viewWidth - this.worldName.getWidth()) / 2.0F,
                this.layout.viewY + this.layout.viewHeight + BOTTOM_BAR_Y_OFFSET);
    }

    // ---------------------------------------------------------------------------------------
    // Door matching
    // ---------------------------------------------------------------------------------------

    /** Whether a door of this colour in another world matches the colour currently picked. */
    private boolean doorMatches(DoorColor other) {
        return this.filter == DoorColor.REGULAR || this.filter.matches(other);
    }

    private boolean isEligible(WorldEntry world) {
        for (DoorColor color : world.doors) {
            if (doorMatches(color)) {
                return true;
            }
        }
        return false;
    }

    /** Regular doors can pick any colour; a coloured door only its own. */
    private boolean isSelectable(DoorColor color) {
        return this.doorColor == DoorColor.REGULAR || color == this.doorColor;
    }

    // ---------------------------------------------------------------------------------------
    // Right-hand panel
    // ---------------------------------------------------------------------------------------

    /** The door colours, as door icons in a grid. Greyed-out colours can't be picked from this door. */
    private final class ColorPicker extends Widget {

        ColorPicker() {
            int count = DoorColor.values().length;
            int rows = (count + SWATCH_COLUMNS - 1) / SWATCH_COLUMNS;
            setSize(SWATCH_COLUMNS * SWATCH_SIZE + (SWATCH_COLUMNS - 1) * SWATCH_GAP,
                    rows * SWATCH_SIZE + (rows - 1) * SWATCH_GAP);
        }

        @Override
        protected boolean isInteractive() {
            return true;
        }

        private int indexAt(int mouseX, int mouseY) {
            for (int i = 0; i < DoorColor.values().length; i++) {
                float sx = swatchX(i);
                float sy = swatchY(i);
                if (mouseX >= sx && mouseX < sx + SWATCH_SIZE && mouseY >= sy && mouseY < sy + SWATCH_SIZE) {
                    return i;
                }
            }
            return -1;
        }

        private float swatchX(int index) {
            return this.x + (index % SWATCH_COLUMNS) * (SWATCH_SIZE + SWATCH_GAP);
        }

        private float swatchY(int index) {
            return this.y + (index / SWATCH_COLUMNS) * (SWATCH_SIZE + SWATCH_GAP);
        }

        @Override
        public void onMouseClicked(int mouseX, int mouseY) {
            int index = indexAt(mouseX, mouseY);
            if (index < 0) {
                return;
            }

            DoorColor color = DoorColor.values()[index];
            if (!isSelectable(color)) {
                return;
            }

            filter = color;
            if (selected >= 0 && !isEligible(worlds.get(selected))) {
                selected = -1;
            }
        }

        @Override
        protected void draw(Font font, int mouseX, int mouseY) {
            for (int i = 0; i < DoorColor.values().length; i++) {
                DoorColor color = DoorColor.values()[i];
                boolean selectable = isSelectable(color);
                float sx = swatchX(i);
                float sy = swatchY(i);

                boolean hover = selectable && i == indexAt(mouseX, mouseY);
                float shade = hover ? 0.10f : 0.0f;
                Gui2D.fillRect(sx, sy, SWATCH_SIZE, SWATCH_SIZE, shade, shade, shade, 1.0f);
                Bevel.ring(sx, sy, SWATCH_SIZE, SWATCH_SIZE, GameFrame.UI_SCALE, false,
                        SLOT_SHADOW, SLOT_LIGHT, 1.0f, 1.0f, 1.0f);

                if (color == filter) {
                    Gui2D.fillRect(sx, sy, SWATCH_SIZE, 2, 1.0f, 1.0f, 1.0f, 0.9f);
                    Gui2D.fillRect(sx, sy + SWATCH_SIZE - 2, SWATCH_SIZE, 2, 1.0f, 1.0f, 1.0f, 0.9f);
                    Gui2D.fillRect(sx, sy, 2, SWATCH_SIZE, 1.0f, 1.0f, 1.0f, 0.9f);
                    Gui2D.fillRect(sx + SWATCH_SIZE - 2, sy, 2, SWATCH_SIZE, 1.0f, 1.0f, 1.0f, 0.9f);
                }

                DoorIcons.draw(color, false, sx + 3, sy + 3, SWATCH_SIZE - 6, selectable ? 1.0f : DIMMED);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // World list (drawn in the world view, like WorldListScreen)
    // ---------------------------------------------------------------------------------------

    private int slotCount() {
        return Math.max(TOTAL_WORLD_SLOTS, this.worlds.size());
    }

    private int visibleRows() {
        int available = this.layout.viewHeight - LIST_TOP;
        return Math.max(1, (available + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
    }

    private int maxScroll() {
        if (this.layout == null) {
            return 0;
        }
        return Math.max(0, slotCount() - visibleRows());
    }

    private int rowX() {
        return this.layout.viewX + (this.layout.viewWidth - ROW_WIDTH) / 2;
    }

    private int rowY(int slot) {
        return this.layout.viewY + LIST_TOP + (slot - this.scrollRows) * (ROW_HEIGHT + ROW_GAP);
    }

    /** The world (not empty slot) row under the point, or -1. */
    private int worldIndexAt(int mouseX, int mouseY) {
        if (this.layout == null || mouseX < rowX() || mouseX > rowX() + ROW_WIDTH) {
            return -1;
        }

        int last = Math.min(this.worlds.size(), this.scrollRows + visibleRows());
        for (int i = this.scrollRows; i < last; i++) {
            if (mouseY >= rowY(i) && mouseY <= rowY(i) + ROW_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY) {
        if (!isFrameOpen()) {
            return;
        }

        int index = worldIndexAt(mouseX, mouseY);
        if (index >= 0 && isEligible(this.worlds.get(index))) {
            this.selected = index;
            this.game.travelThroughDoor(this.worlds.get(index).name, this.filter);
            return;
        }

        // Widgets in the panel (the colour picker).
        super.mouseClicked(mouseX, mouseY);
    }

    @Override
    protected void renderForeground(int mouseX, int mouseY) {
        if (!isFrameOpen()) {
            this.hovered = -1;
            return;
        }

        this.scrollRows = Math.min(this.scrollRows, maxScroll());
        this.hovered = worldIndexAt(mouseX, mouseY);

        Font font = this.game.getFont();

        Gui2D.begin(this.game.getWidth(), this.game.getHeight());

        int x = this.layout.viewX;
        int y = this.layout.viewY;
        int w = this.layout.viewWidth;
        int h = this.layout.viewHeight;

        Gui2D.fillRect(x, y, w, h, 0.0F, 0.0F, 0.0F, 0.55F);

        String title = "Select World";
        font.drawString(title, x + (w - font.width(title)) / 2, y + TITLE_Y, 0xFFFFFF);

        int last = Math.min(slotCount(), this.scrollRows + visibleRows());
        for (int slot = this.scrollRows; slot < last; slot++) {
            if (slot < this.worlds.size()) {
                drawWorldRow(font, slot);
            } else {
                drawEmptyRow(font, slot);
            }
        }

        if (this.scrollRows > 0 || last < slotCount()) {
            String hint = "scroll for more";
            font.drawString(hint, x + w - font.width(hint) - 8, y + h - 12, UiStyle.TEXT_DIM);
        }

        Gui2D.end();
    }

    /** The sunken slot look shared with the world list: a solid inset with the inventory's bevel. */
    private static void drawSlot(float x, float y, float shade, float bevelStrength) {
        Gui2D.fillRect(x, y, ROW_WIDTH, ROW_HEIGHT, shade, shade, shade, 1.0f);
        Bevel.ring(x, y, ROW_WIDTH, ROW_HEIGHT, GameFrame.UI_SCALE, false, SLOT_SHADOW, SLOT_LIGHT,
                bevelStrength, bevelStrength, bevelStrength);
    }

    /** Width the door badges take from the row's right edge, margin included (0 = no doors). */
    private static int badgesWidth(WorldEntry world) {
        int count = world.doors.size();
        return count == 0 ? 0 : count * DOOR_ICON_SIZE + (count - 1) * DOOR_ICON_GAP + BADGE_MARGIN;
    }

    /** The text cut short with "..." so it fits in maxWidth pixels. */
    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + ellipsis) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + ellipsis;
    }

    private void drawWorldRow(Font font, int slot) {
        WorldEntry world = this.worlds.get(slot);
        boolean eligible = isEligible(world);
        boolean rowHovered = eligible && slot == this.hovered;

        int rx = rowX();
        int ry = rowY(slot);

        drawSlot(rx, ry, rowHovered ? 0.10f : 0.0f, 1.0f);
        if (slot == this.selected) {
            int tint = getFrameTint();
            Gui2D.fillRect(rx, ry, 3, ROW_HEIGHT,
                    ((tint >> 16) & 0xFF) / 255.0f, ((tint >> 8) & 0xFF) / 255.0f, (tint & 0xFF) / 255.0f, 1.0f);
        }

        // The name gets whatever width the badges leave, and ends in "..." if it doesn't fit.
        int nameColor = !eligible ? 0x666666 : rowHovered ? UiStyle.TEXT_HOVER : UiStyle.TEXT;
        int nameSpace = ROW_WIDTH - TEXT_X - NAME_BADGE_GAP - badgesWidth(world);
        font.drawString(fit(font, world.name, nameSpace), rx + TEXT_X, ry + TEXT_Y, nameColor);

        // This world's doors on the right, brightest where they match the colour being looked for.
        int iconX = rx + ROW_WIDTH - badgesWidth(world);
        int iconY = ry + (ROW_HEIGHT - DOOR_ICON_SIZE) / 2;
        for (DoorColor color : world.doors) {
            DoorIcons.draw(color, false, iconX, iconY, DOOR_ICON_SIZE, doorMatches(color) ? 1.0f : DIMMED);
            iconX += DOOR_ICON_SIZE + DOOR_ICON_GAP;
        }
    }

    private void drawEmptyRow(Font font, int slot) {
        int rx = rowX();
        int ry = rowY(slot);

        drawSlot(rx, ry, 0.0f, 0.5f);
        String emptyLabel = "- Empty World Slot " + (slot + 1) + " -";
        font.drawString(emptyLabel, rx + (ROW_WIDTH - font.width(emptyLabel)) / 2, ry + TEXT_Y, 0x777777);
    }
}