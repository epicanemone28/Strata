package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.FramePanelScreen;
import com.nikohalfkino.rubydung.interfaces.GameFrame;
import com.nikohalfkino.rubydung.interfaces.widget.PanelSection;
import com.nikohalfkino.rubydung.interfaces.TileIconRenderer;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.SlotGrid;
import com.nikohalfkino.rubydung.level.tiles.Tile;
import com.nikohalfkino.rubydung.level.tiles.TreeTile;
import com.nikohalfkino.rubydung.render.Font;
import com.nikohalfkino.rubydung.util.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * while open, GameFrame shrinks the world view and slides the panel in (being a Screen, the camera gets no mouse input)
 *
 * two slot grids, "Palette" (ordinary tiles) and "Props" (billboarded tiles), each a SlotGrid pinned to its panel section every frame
 *
 * add another group with another addGrid(...) call in the constructor
 */
public class InventoryScreen extends FramePanelScreen {

    private static final float LABEL_SCALE = 2.0F;
    private static final int LABEL_MARGIN = -2;
    private static final int LABEL_COLOR = 0xFFFFFF;

    private final List<String> titles = new ArrayList<>();
    private final List<SlotGrid<Tile>> grids = new ArrayList<>();
    // side length (in slots) of each grid, squares sized to their contents
    private final List<Integer> sides = new ArrayList<>();

    // last mouse position seen by render(), so wheel scrolling hits the grid under the cursor
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    public InventoryScreen(RubyDung game) {
        super(game);

        // Only the placeable tiles (Tile.getTiles() would also include AIR).
        List<Tile> tiles = new ArrayList<>();
        List<Tile> props = new ArrayList<>();
        for (Tile tile : Tile.getCycleOrder()) {
            (tile.isBillboard() ? props : tiles).add(tile);
        }

        addGrid("Palette", tiles);
        addGrid("Props", props);

        addCornerButton("Done", this.game::closeScreen);
    }

    private void addGrid(String title, List<Tile> items) {
        if (items.isEmpty()) {
            return;
        }

        SlotGrid<Tile> grid = new SlotGrid<Tile>()
                .setItems(items)
                .selectedWhen(tile -> tile == this.game.getPlayerInteraction().getSelectedTile())
                .onSelect(tile -> this.game.getPlayerInteraction().setSelectedTile(tile))
                .setIconDrawer((tile, cx, cy, size, w, h) ->
                        TileIconRenderer.draw(tile, this.game.getLevel(), cx, cy, size, w, h))
                .setOverlayDrawer(InventoryScreen::drawSizeLabel);

        // The smallest square that holds every item, at most the full-size grid (then it scrolls).
        int side = Math.min(GameFrame.GRID_ROWS, (int) Math.ceil(Math.sqrt(items.size())));

        this.titles.add(title);
        this.sides.add(side);
        this.grids.add(add(grid, Anchor.TOP_LEFT, 0, 0));
    }

    /** scrolls the grid under the mouse (the first one if none), positive = down */
    public void scroll(int rows) {
        for (SlotGrid<Tile> grid : this.grids) {
            if (grid.contains(this.lastMouseX, this.lastMouseY)) {
                grid.scroll(rows);
                return;
            }
        }
        if (!this.grids.isEmpty()) {
            this.grids.get(0).scroll(rows);
        }
    }

    @Override
    public boolean showsSlots() {
        return true;
    }

    @Override
    public String getPanelTitle() {
        return "Palette";
    }

    @Override
    public int getFrameTint() {
        return 0xFFFFFF;
    }

    /** Palette on top, Props below, each a square just big enough for its tiles (shrinks if the window is short) */
    @Override
    public List<PanelSection> getSections() {
        List<PanelSection> sections = new ArrayList<>();
        for (int i = 0; i < this.titles.size(); i++) {
            int side = this.sides.get(i);
            sections.add(PanelSection.slots(this.titles.get(i), side, side));
        }
        return sections;
    }

    @Override
    protected void placeWidgets() {
        for (int i = 0; i < this.grids.size(); i++) {
            placeInSection(this.grids.get(i), i);
        }
    }

    @Override
    public boolean keyPressed(int key, char character) {
        if (key == KeyBinding.INVENTORY.getKey() || key == Keyboard.KEY_ESCAPE) {
            this.game.closeScreen();
            return true;
        }
        return false;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        super.render(mouseX, mouseY);
    }

    // S / M / L in the slot's top-right corner, for tiles that have one
    private static void drawSizeLabel(Font font, Tile tile, int slotX, int slotY, int slotSize) {
        String label = sizeLabel(tile);
        if (label == null) {
            return;
        }

        int labelWidth = (int) (font.getWidth(label) * LABEL_SCALE);
        int x = slotX + slotSize - labelWidth - LABEL_MARGIN;
        int y = slotY + LABEL_MARGIN;
        font.drawStringScaled(label, x, y, LABEL_COLOR, LABEL_SCALE);
    }

    // "S", "M" or "L" for multi-size tiles, null otherwise. add other multi-size types here
    private static String sizeLabel(Tile tile) {
        if (tile instanceof TreeTile) {
            TreeTile.TreeType type = ((TreeTile) tile).getType();
            if (type == TreeTile.TreeType.SAPLING) {
                return "S";
            }
            if (type == TreeTile.TreeType.MEDIUM) {
                return "M";
            }
            if (type == TreeTile.TreeType.LARGE) {
                return "L";
            }
        }
        return null;
    }
}