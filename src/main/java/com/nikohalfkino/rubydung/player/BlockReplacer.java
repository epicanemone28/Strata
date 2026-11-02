package com.nikohalfkino.rubydung.player;

import com.nikohalfkino.rubydung.interfaces.hud.HandHud;
import com.nikohalfkino.rubydung.level.tiles.Tile;

import java.util.List;

import static com.nikohalfkino.rubydung.player.InteractionContext.*;

/** Replacing blocks (both mouse buttons held): swaps what the crosshair sweeps over for the selected tile. */
final class BlockReplacer {

    private final InteractionContext ctx;
    private final BlockPlacer placer;

    /** Like the breaker's bank: distance travelled that pays for extra replacements. */
    private double replaceBank = 0.0;

    BlockReplacer(InteractionContext ctx, BlockPlacer placer) {
        this.ctx = ctx;
        this.placer = placer;
    }

    void resetBank() {
        this.replaceBank = 0.0;
    }

    /**
     * One tick of holding both buttons. Works like breaking: the normal repeat rate when standing
     * still, and while moving the distance travelled pays for extra replacements, so sweeping the
     * crosshair over blocks (or flying along them) replaces every one the crosshair passed over,
     * not only the one it ends up on.
     */
    void tick(List<HitResult> hits, double speed) {
        boolean moving = speed > MOVING_EPSILON;

        int allowance = this.ctx.actionCooldown <= 0 ? 1 : 0;
        if (moving) {
            this.replaceBank = Math.min(this.replaceBank + speed, MAX_BREAKS_PER_TICK);
            allowance = Math.max(allowance, (int) this.replaceBank);
        }

        if (this.ctx.actionCooldown > 0) {
            this.ctx.actionCooldown--;
        }
        if (allowance <= 0) {
            return;
        }

        int replaced = replaceCells(hits, allowance);
        if (replaced > 0) {
            this.ctx.acted = true;
            this.ctx.actionCooldown = BREAK_DELAY_TICKS - 1;
            if (moving) {
                this.replaceBank = Math.max(0.0, this.replaceBank - replaced);
            }
        }
    }

    /** Replaces up to {@code allowance} of the hits, skipping ones that are gone or already the selected tile. */
    private int replaceCells(List<HitResult> hits, int allowance) {
        int replaced = 0;
        for (HitResult hit : hits) {
            if (replaced >= allowance) {
                break;
            }
            if (replaceAt(hit)) {
                replaced++;
            }
        }
        return replaced;
    }

    private boolean replaceAt(HitResult hit) {
        if (hit == null) {
            return false;
        }

        if (hit.overlayId >= 0) {
            return false; // replacing a tile that shares a cell: not supported (break it, then place)
        }

        if (!this.ctx.hasTarget(hit)) {
            return false; // already gone: there is nothing to replace
        }

        Tile selected = this.ctx.selectedTile;

        this.ctx.aim.aim(hit, hit.cellX, hit.cellY, hit.cellZ);
        int newData = this.placer.axisForFace(hit.face);
        this.ctx.aim.clear();

        // Already the same block: nothing to do.
        if (this.ctx.level.getTile(hit.x, hit.y, hit.z) == selected.id
                && this.ctx.level.getData(hit.x, hit.y, hit.z) == newData) {
            return false;
        }

        if (selected.isMultiTile()) {
            return replaceWithMultiTile(hit);
        }

        if (this.placer.intersectsPlayer(hit.x, hit.y, hit.z, newData)
                || !this.ctx.level.canSetHost(hit.x, hit.y, hit.z, selected, newData)) {
            return false;
        }

        this.ctx.level.setTile(hit.x, hit.y, hit.z, selected.id, newData);
        HandHud.swingCurrent(HandHud.Action.PLACE);
        return true;
    }

    /**
     * Replaces the targeted block (or whole multi-tile object) with a multi-tile object anchored at
     * the target's origin. The target is cleared first so its own cells count as free; if the new
     * object still doesn't fit, the original is put back.
     */
    private boolean replaceWithMultiTile(HitResult hit) {
        Tile selected = this.ctx.selectedTile;
        int oldId = this.ctx.level.getTile(hit.x, hit.y, hit.z);
        int oldData = this.ctx.level.getData(hit.x, hit.y, hit.z);
        int rotation = this.placer.placementDirection();

        this.ctx.level.setTile(hit.x, hit.y, hit.z, 0);

        if (!this.ctx.level.canPlaceTile(selected, hit.x, hit.y, hit.z, rotation)
                || this.placer.footprintIntersectsPlayer(hit.x, hit.y, hit.z, rotation)) {
            this.ctx.level.setTile(hit.x, hit.y, hit.z, oldId, oldData);
            return false;
        }

        this.ctx.level.setTile(hit.x, hit.y, hit.z, selected.id, rotation);
        HandHud.swingCurrent(HandHud.Action.PLACE);
        return true;
    }
}
