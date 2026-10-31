package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.DoorIcons;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;
import com.nikohalfkino.rubydung.level.DoorRegistry;
import com.nikohalfkino.rubydung.level.Tiles.DoorColor;
import com.nikohalfkino.rubydung.level.WorldManager;
import com.nikohalfkino.rubydung.render.Font;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class WorldListScreen extends MenuScreen {

    private static final SimpleDateFormat LAST_PLAYED_FORMAT = new SimpleDateFormat("MMM d, HH:mm");

    private static final float ROW_WIDTH = UiStyle.PANEL_WIDTH;
    private static final float ROW_HEIGHT = 32.0f;
    private static final float ROW_GAP = 4.0f;
    private static final float DELETE_ZONE_WIDTH = 70.0f;
    private static final int TOTAL_WORLD_SLOTS = 10;

    private static final float BUTTON_SPACING = 8.0f;

    private static final float DOOR_ICON_SIZE = ROW_HEIGHT - 8.0f;
    private static final float DOOR_ICON_GAP = 4.0f;

    private final List<WorldRow> rows = new ArrayList<>();

    private List<WorldManager.WorldInfo> worlds = new ArrayList<>();
    private String pendingDelete = null;

    public WorldListScreen(RubyDung game) {
        super(game);

        onEscape(() -> game.openScreen(new TitleScreen(game)));

        addTitle("Select World", 15.0f);

        Column list = addColumn(ROW_GAP, 45.0f);
        for (int i = 0; i < TOTAL_WORLD_SLOTS; i++) {
            this.rows.add(list.add(new WorldRow(i + 1)));
        }
        // Original spacing between the last row and the buttons: row gap + 10.
        this.rows.get(TOTAL_WORLD_SLOTS - 1).setGapAfter(ROW_GAP + 10.0f);

        list.add(new Button("New World").onClick(() -> game.openScreen(new CreateWorldScreen(game))), BUTTON_SPACING);
        list.add(new Button("Back").onClick(() -> game.openScreen(new TitleScreen(game))));
    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);
        refresh();
    }

    private void refresh() {
        this.worlds = WorldManager.listWorlds();
        this.pendingDelete = null;

        for (int i = 0; i < this.rows.size(); i++) {
            this.rows.get(i).setWorld(i < this.worlds.size() ? this.worlds.get(i) : null);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY) {
        // Any click that isn't on a world row cancels a pending "Are you sure?".
        if (!(this.root.pick(mouseX, mouseY) instanceof WorldRow)) {
            this.pendingDelete = null;
        }
        super.mouseClicked(mouseX, mouseY);
    }

    /** One slot of the list: a saved world (click to play, right side to delete) or an empty placeholder. */
    private final class WorldRow extends Widget {

        private final int slotNumber;
        private WorldManager.WorldInfo world;

        /** Colours of the dimensional doors in this world, shown on the right of the row. */
        private Set<DoorColor> doors = EnumSet.noneOf(DoorColor.class);

        WorldRow(int slotNumber) {
            this.slotNumber = slotNumber;
            setSize(ROW_WIDTH, ROW_HEIGHT);
        }

        void setWorld(WorldManager.WorldInfo world) {
            this.world = world;
            this.doors = world == null
                    ? EnumSet.noneOf(DoorColor.class)
                    : DoorRegistry.colors(world.name);
        }

        @Override
        protected boolean isInteractive() {
            return this.world != null;
        }

        private boolean inDeleteZone(int mouseX, int mouseY) {
            return mouseX >= this.x + ROW_WIDTH - DELETE_ZONE_WIDTH && mouseX <= this.x + ROW_WIDTH
                    && mouseY >= this.y && mouseY <= this.y + ROW_HEIGHT;
        }

        @Override
        public void onMouseClicked(int mouseX, int mouseY) {
            if (this.world.name.equals(pendingDelete)) {
                WorldManager.delete(this.world.name);
                refresh();
            } else if (inDeleteZone(mouseX, mouseY)) {
                pendingDelete = this.world.name;
            } else {
                game.loadWorld(this.world.name);
            }
        }

        @Override
        protected void draw(Font font, int mouseX, int mouseY) {
            if (this.world == null) {
                drawEmpty(font);
            } else {
                drawWorld(font, mouseX, mouseY);
            }
        }

        private void drawWorld(Font font, int mouseX, int mouseY) {
            if (this.world.name.equals(pendingDelete)) {
                Gui2D.fillRect(this.x, this.y, ROW_WIDTH, ROW_HEIGHT, 0.35f, 0.1f, 0.1f, 0.9f);
                String prompt = "Are you sure? (Click again to confirm)";
                font.drawString(prompt, (int) (this.x + 8), (int) (this.y + 8), 0xFFAAAA);
                return;
            }

            boolean deleteHovered = inDeleteZone(mouseX, mouseY);
            boolean rowHovered = !deleteHovered && contains(mouseX, mouseY);

            float alpha = (rowHovered || deleteHovered) ? 0.45f : 0.3f;
            Gui2D.fillRect(this.x, this.y, ROW_WIDTH, ROW_HEIGHT, 0.0f, 0.0f, 0.0f, alpha);

            int nameColor = rowHovered ? UiStyle.TEXT_HOVER : UiStyle.TEXT;
            font.drawString(this.world.name, (int) (this.x + 8), (int) (this.y + 8), nameColor);

            int nameWidth = font.width(this.world.name);
            String lastPlayed = "  (" + LAST_PLAYED_FORMAT.format(new Date(this.world.lastPlayed)) + ")";
            font.drawString(lastPlayed, (int) (this.x + 8 + nameWidth), (int) (this.y + 8), UiStyle.TEXT_DIM);

            String deleteLabel = "Delete";
            int deleteWidth = font.width(deleteLabel);
            int deleteColor = deleteHovered ? UiStyle.TEXT_HOVER : UiStyle.TEXT_DANGER;
            font.drawString(deleteLabel, (int) (this.x + ROW_WIDTH - deleteWidth - 8), (int) (this.y + 8), deleteColor);

            // The world's doors, to the left of "Delete".
            float iconX = this.x + ROW_WIDTH - deleteWidth - 8 - DOOR_ICON_GAP - DOOR_ICON_SIZE - 4;
            List<DoorColor> doorList = new ArrayList<>(this.doors);
            Collections.reverse(doorList);
            for (DoorColor color : doorList) {
                DoorIcons.draw(color, false, iconX, this.y + 4, DOOR_ICON_SIZE, 1.0f);
                iconX -= DOOR_ICON_SIZE + DOOR_ICON_GAP;
            }
        }

        private void drawEmpty(Font font) {
            Gui2D.fillRect(this.x, this.y, ROW_WIDTH, ROW_HEIGHT, 0.0f, 0.0f, 0.0f, 0.15f);
            String emptyLabel = "- Empty World Slot " + this.slotNumber + " -";
            int labelWidth = font.width(emptyLabel);
            font.drawString(emptyLabel, (int) (this.x + (ROW_WIDTH - labelWidth) / 2.0f), (int) (this.y + 8), 0x555555);
        }
    }
}