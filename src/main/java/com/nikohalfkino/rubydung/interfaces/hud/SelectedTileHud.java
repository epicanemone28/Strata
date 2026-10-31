package com.nikohalfkino.rubydung.interfaces.hud;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.TileIconRenderer;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;
import com.nikohalfkino.rubydung.level.Tiles.DimensionalDoorTile;
import com.nikohalfkino.rubydung.level.Tiles.Tile;
import com.nikohalfkino.rubydung.level.Tiles.TreeTile;
import com.nikohalfkino.rubydung.render.Font;

public class SelectedTileHud extends Widget {

    private static final float ICON_SIZE = 32.0F;
    private static final float ICON_TOP_MARGIN = 24.0F;
    private static final float SCALE = 2.0F;
    private static final float LINE_STEP = 12 * SCALE;

    private final RubyDung game;

    public SelectedTileHud(RubyDung game) {
        this.game = game;
        setAnchor(Anchor.TOP_LEFT, 10, 10);
    }

    private static String tileName(Tile tile) {
        if (tile instanceof TreeTile) return "Tree";
        if (tile instanceof DimensionalDoorTile) return "Door";
        return formatTileName(tile.name);
    }

    private static float iconTop(Tile tile) {
        float lastLineY = (tile instanceof TreeTile || tile instanceof DimensionalDoorTile) ? LINE_STEP : 0.0f;
        return lastLineY + LINE_STEP + ICON_TOP_MARGIN;
    }

    @Override
    protected float naturalWidth(Font font) {
        Tile tile = this.game.getSelectedTile();
        float widest = font.getWidth("Selected: " + tileName(tile)) * SCALE;
        if (tile instanceof TreeTile) {
            widest = Math.max(widest, font.getWidth("Type: " + ((TreeTile) tile).getType().displayName) * SCALE);
        }
        if (tile instanceof DimensionalDoorTile) {
            widest = Math.max(widest, font.getWidth("Type: " + ((DimensionalDoorTile) tile).getColor().getDisplayName()) * SCALE);
        }
        return Math.max(widest, ICON_SIZE);
    }

    @Override
    protected float naturalHeight(Font font) {
        return iconTop(this.game.getSelectedTile()) + ICON_SIZE;
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        Tile tile = this.game.getSelectedTile();

        String prefix = "Selected: ";
        int left = (int) this.x;
        int top = (int) this.y;

        font.drawStringScaled(prefix, left, top, 0xFFFFFF, SCALE);

        int prefixWidth = (int) (font.getWidth(prefix) * SCALE);

        font.drawStringScaled(tileName(tile), left + prefixWidth, top, 0xFFFF00, SCALE);

        if (tile instanceof TreeTile) {
            TreeTile treeTile = (TreeTile) tile;

            String typePrefix = "Type: ";
            String typeValue = treeTile.getType().displayName;

            int secondLineY = top + (int) LINE_STEP;

            font.drawStringScaled(typePrefix, left, secondLineY, 0xFFFFFF, SCALE);

            int typePrefixWidth = (int) (font.getWidth(typePrefix) * SCALE);

            font.drawStringScaled(typeValue, left + typePrefixWidth, secondLineY, 0xFFFF00, SCALE);
        }

        if (tile instanceof DimensionalDoorTile) {
            DimensionalDoorTile door = (DimensionalDoorTile) tile;

            String typePrefix = "Type: ";
            String typeValue = door.getColor().getDisplayName();

            int secondLineY = top + (int) LINE_STEP;

            font.drawStringScaled(typePrefix, left, secondLineY, 0xFFFFFF, SCALE);

            int typePrefixWidth = (int) (font.getWidth(typePrefix) * SCALE);

            font.drawStringScaled(typeValue, left + typePrefixWidth, secondLineY, 0xFFFF00, SCALE);
        }

        int iconTopY = (int) (top + iconTop(tile));

        TileIconRenderer.draw(tile, this.game.getLevel(),
                left + ICON_SIZE / 2.0F, iconTopY + ICON_SIZE / 2.0F, ICON_SIZE,
                (int) getRootWidth(), (int) getRootHeight());
    }

    private static String formatTileName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "";
        }

        String[] words = rawName.split("_");
        StringBuilder formattedName = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                formattedName.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());

                if (i < words.length - 1) {
                    formattedName.append(" ");
                }
            }
        }

        return formattedName.toString();
    }
}