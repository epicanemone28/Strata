package com.nikohalfkino.rubydung.level.Tiles;

import com.nikohalfkino.rubydung.assets.*;
import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.phys.AABB;
import com.nikohalfkino.rubydung.render.Tessellator;
import com.nikohalfkino.rubydung.render.Textures;
import com.nikohalfkino.rubydung.util.Axis;
import com.nikohalfkino.rubydung.util.Direction;
import com.nikohalfkino.rubydung.util.Registry;

import java.util.*;

public class Tile {

    public static final int FACE_BOTTOM = 0;
    public static final int FACE_TOP = 1;
    public static final int FACE_NORTH = 2;
    public static final int FACE_SOUTH = 3;
    public static final int FACE_WEST = 4;
    public static final int FACE_EAST = 5;

    private static final String BLOCK_TEXTURE_PATH = "/assets/rubydung/textures/block/";
    private static final String MISSING_TEXTURE = "/assets/rubydung/textures/missing.png";

    private static final Registry<Tile> REGISTRY = new Registry<>();
    private static final Map<String, Tile> BY_NAME = new HashMap<>();

    public static final Tile AIR = REGISTRY.register(new Tile("air").setSolid(false));
    public static final Tile GRASS = REGISTRY.register(new Tile("grass"));
    public static final Tile COBBLESTONE = REGISTRY.register(new Tile("cobblestone"));
    public static final Tile SLATE = REGISTRY.register(new Tile("slate"));
    public static final Tile BRICKS = REGISTRY.register(new Tile("bricks"));
    public static final Tile METAL = REGISTRY.register(new Tile("metal"));

    public static final Tile METAL_GIRDER = REGISTRY.register(
            new RotatableTile("metal_girder", "metal_girder")
                    .setTransparent(true)
                    .setTexture(FACE_TOP, "metal_girder_middle")
                    .setTexture(FACE_BOTTOM, "metal_girder_middle")
    );

    public static final Tile METAL_GRATE = REGISTRY.register(
            new Tile("metal_grate", "metal_grate")
                    .setTransparent(true)
                    .setTexture(FACE_TOP, "metal_grate")
                    .setTexture(FACE_BOTTOM, "empty")
                    .setTexture(FACE_NORTH, "metal_grate_side")
                    .setTexture(FACE_SOUTH, "metal_grate_side")
                    .setTexture(FACE_WEST, "metal_grate_side")
                    .setTexture(FACE_EAST, "metal_grate_side")
    );

    public static final Tile GOLD = REGISTRY.register(
            new Tile("gold").setTransparent(true)
    );

    public static final Tile GLASS = REGISTRY.register(
            new Tile("glass").setTransparent(true)
    );

    public static final Tile TREE_SAPLING = REGISTRY.register(
            new TreeTile("tree_sapling", TreeTile.TreeType.SAPLING)
    );
    public static final Tile TREE_MEDIUM = REGISTRY.register(
            new TreeTile("tree_medium", TreeTile.TreeType.MEDIUM)
    );
    public static final Tile TREE_LARGE = REGISTRY.register(
            new TreeTile("tree_large", TreeTile.TreeType.LARGE)
    );

    public static final Tile TREE = TREE_MEDIUM;

    public static final Tile LAMPPOST = REGISTRY.register(
            new LamppostTile("lamppost")
    );

    public static final Tile STONE = REGISTRY.register(new Tile("stone"));
    public static final Tile GRAVEL = REGISTRY.register(new Tile("gravel"));
    public static final Tile SAND = REGISTRY.register(new Tile("sand"));
    public static final Tile PLANKS = REGISTRY.register(new Tile("planks"));
    public static final Tile LOG = REGISTRY.register(
            new RotatableTile("log", "log_side")
                    .setTransparent(true)
                    .setTexture(FACE_TOP, "log_middle")
                    .setTexture(FACE_BOTTOM, "log_middle")
    );
    public static final Tile LAMP = REGISTRY.register(new Tile("lamp"));
    public static final Tile PATH = REGISTRY.register(new Tile("path"));

    // new tiles are appended here so existing ids don't shift
    public static final Tile BRICKS_LARGE = REGISTRY.register(new Tile("bricks_large"));
    public static final Tile STONE_BRICKS = REGISTRY.register(new Tile("stone_bricks"));
    public static final Tile STONE_BRICKS_LARGE = REGISTRY.register(new Tile("stone_bricks_large"));

    public static final Tile FUCKED_SLAB = REGISTRY.register(new Tile("fucked_slab").setModel("rubydung:block/fucked_slab"));
    public static final Tile TABLE = REGISTRY.register(new Tile("table").setModel("rubydung:block/table"));

    // Rotatable like LOG: the default texture is the side, TOP/BOTTOM are the end caps
    // ("middle"), and RotatableTile swaps them around when the block is placed on its side.
    public static final Tile PILLAR = REGISTRY.register(
            new RotatableTile("pillar", "pillar_side")
                    .setTexture(FACE_TOP, "pillar_middle")
                    .setTexture(FACE_BOTTOM, "pillar_middle")
    );
    public static final Tile PILLAR_TOP = REGISTRY.register(
            new RotatableTile("pillar_top", "pillar_side_top")
                    .setTexture(FACE_TOP, "pillar_middle")
                    .setTexture(FACE_BOTTOM, "pillar_middle")
    );
    public static final Tile PILLAR_BOTTOM = REGISTRY.register(
            new RotatableTile("pillar_bottom", "pillar_side_bottom")
                    .setTexture(FACE_TOP, "pillar_middle")
                    .setTexture(FACE_BOTTOM, "pillar_middle")
    );

    public static final Tile COLUMN = REGISTRY.register(new ColumnTile("column"));

    public static final Tile MARBLE = REGISTRY.register(new Tile("marble"));

    // separate registration for the placeable tiles cuz id shifts corrupt worlds
    // (this list only controls the order tiles are cycled through - reordering it is safe,
    // unlike reordering the registrations above)
    private static final List<Tile> CYCLE_ORDER = Arrays.asList(
            // terrain
            GRASS, SAND, GRAVEL, STONE, COBBLESTONE, SLATE,
            // bricks
            BRICKS, BRICKS_LARGE, STONE_BRICKS, STONE_BRICKS_LARGE,
            // pillars & columns
            MARBLE, PILLAR_TOP, PILLAR, PILLAR_BOTTOM, COLUMN,
            // metal
            METAL, METAL_GIRDER, METAL_GRATE, GOLD, FUCKED_SLAB, TABLE,
            // wood
            LOG, PLANKS,
            // glass & lighting
            GLASS, LAMP, LAMPPOST,
            // trees
            TREE_SAPLING, TREE_MEDIUM, TREE_LARGE
    );

    public final int id;
    public final String name;

    private final String[] faceTextures = new String[6];
    private final int[] faceAtlasSlots = new int[6];

    private boolean isSolid = true;
    private boolean isTransparent = false;

    private Model baseModel;

    public Model getModel(){
        return this.baseModel;
    }

    public Tile(String name) {
        this(name, name);
    }

    public Tile(String name, String defaultTextureName) {
        this.id = REGISTRY.nextId();
        this.name = name;

        setTexture(defaultTextureName);

        this.baseModel = new Model(name);
        this.baseModel.addSimplestCube(0,0,0,16,16,16,"rubydung:block/"+defaultTextureName);

        BY_NAME.put(name, this);
    }

    public static Tile byId(int id) {
        return REGISTRY.get(id);
    }

    public static Tile byName(String name) {
        return BY_NAME.get(name);
    }

    public static List<Tile> getTiles() {
        return REGISTRY.entries();
    }

    public static List<Tile> getCycleOrder() {
        return Collections.unmodifiableList(CYCLE_ORDER);
    }

    public boolean isBillboard() {
        return false;
    }

    public void renderBillboard(Tessellator tessellator, Level level, int x, int y, int z, int totalAtlasSlots) {
    }

    public AABB getAABB(int x, int y, int z) {
        if (!this.isSolid()) {
            return null;
        }
        return new AABB(x, y, z, x + 1.0F, y + 1.0F, z + 1.0F);
    }

    public AABB getPickAABB(int x, int y, int z) {
        return getAABB(x, y, z);
    }

    public Tile setModel(String path){
        this.baseModel = Model.fromAsset(AssetPath.parse(path).asModelPath());
        return this;
    }

    public Tile setTexture(String textureName) {
        for (int face = 0; face < 6; face++) {
            setTexture(face, textureName);
        }
        return this;
    }

    public Tile setTexture(int face, String textureName) {
        String path = BLOCK_TEXTURE_PATH + textureName + ".png";
        this.faceTextures[face] = Textures.resourceExists(path) ? path : MISSING_TEXTURE;
        return this;
    }

    public Tile setTextures(String topBottomTextureName, String sideTextureName) {
        setTexture(FACE_TOP, topBottomTextureName);
        setTexture(FACE_BOTTOM, topBottomTextureName);
        setTexture(FACE_NORTH, sideTextureName);
        setTexture(FACE_SOUTH, sideTextureName);
        setTexture(FACE_WEST, sideTextureName);
        setTexture(FACE_EAST, sideTextureName);
        return this;
    }

    public String getTexture(int face) {
        return this.faceTextures[face];
    }

    public void setAtlasSlot(int face, int slot) {
        this.faceAtlasSlots[face] = slot;
    }

    public int getAtlasSlot(int face) {
        return this.faceAtlasSlots[face];
    }

    public boolean isSolid() {
        return this.isSolid;
    }

    public Tile setSolid(boolean solid) {
        this.isSolid = solid;
        return this;
    }

    public boolean isTransparent() {
        return this.isTransparent;
    }

    public Tile setTransparent(boolean transparent) {
        this.isTransparent = transparent;
        return this;
    }

    protected boolean shouldRenderFace(Level level, int x, int y, int z) {
        int tileId = level.getTile(x, y, z);
        Tile neighbour = Tile.byId(tileId);

        if (neighbour == null || neighbour == Tile.AIR) {
            return true;
        }

        if (neighbour.isTransparent() || this.isTransparent) {
            return true;
        }

        if (neighbour.isSolid()) {
            return false;
        }

        return true;
    }

    public void render(Tessellator tessellator, Level level, int layer, int x, int y, int z, TextureAtlas blockAtlas) {
        render(tessellator, level, layer, x, y, z, blockAtlas, 0);
    }

    public ArrayList<Axis> rotations = new ArrayList<>();

    public void render(Tessellator tessellator, Level level, int layer, int x, int y, int z, TextureAtlas blockAtlas, int direction) {
        float shadeX = 0.6f;
        float shadeY = 1.0f;
        float shadeZ = 0.8f;

        Model model = getModel();

        float minX = x + 0.0F;
        float maxX = x + 1.0F;
        float minY = y + 0.0F;
        float maxY = y + 1.0F;
        float minZ = z + 0.0F;
        float maxZ = z + 1.0F;

        for(ModelElement e : model.elements){

            for(Axis axis : rotations) e = e.rotatedCopy(axis);

            int i = 0;
            for(ModelFace face : e.faces){
                Direction d = face.direction;

                float[] textureUV = blockAtlas.atlasLookup.get(face.texturePath);
                float minU = textureUV[0] + (textureUV[2]-textureUV[0]) * face.minU;
                float maxU = textureUV[0] + (textureUV[2]-textureUV[0]) * face.maxU;
                float minV = textureUV[1] + (textureUV[3]-textureUV[1]) * face.minV;
                float maxV = textureUV[1] + (textureUV[3]-textureUV[1]) * face.maxV;

                /*
                int slot = getAtlasSlot(FACE_TOP);
                float AminU = slot / (float) blockAtlas;
                float AmaxU = AminU + 1.0F / totalAtlasSlots;
                float AminV = 0.0F;
                float AmaxV = 1.0F;

                float minU = AminU + (AmaxU-AminU) * face.minU;
                float maxU = AminU + (AmaxU-AminU) * face.maxU;
                float minV = AminV + (AmaxV-AminV) * face.minV;
                float maxV = AminV + (AmaxV-AminV) * face.maxV;
                 */



                if(face.cullDirection != null){
                    Direction cd = face.cullDirection;
                    if(!shouldRenderFace(level,x+cd.x,y+cd.y,z+cd.z)) continue;
                }

                float shade = 1.0f;
                switch(d.axis){
                    case X: shade = shadeX; break;
                    case Y: shade = shadeY; break;
                    case Z: shade = shadeZ; break;
                }

                float brightness = level.getBrightness(x + d.x, y + d.y, z + d.z);
                brightness *= shade;

                if (layer == 1 ^ brightness == shade) {
                    tessellator.color(brightness,brightness,brightness);
                    float[][] verts = face.getQuadFromCube(e,minX,minY,minZ);
                    tessellator.texture(maxU, maxV);
                    tessellator.vertex(verts[Math.floorMod(0+face.rotation,4)]);
                    tessellator.texture(maxU, minV);
                    tessellator.vertex(verts[Math.floorMod(1+face.rotation,4)]);
                    tessellator.texture(minU, minV);
                    tessellator.vertex(verts[Math.floorMod(2+face.rotation,4)]);
                    tessellator.texture(minU, maxV);
                    tessellator.vertex(verts[Math.floorMod(3+face.rotation,4)]);
                }
            }
        }

    }

    public void renderFace(Tessellator tessellator, int x, int y, int z, int face) {
        AABB aabb = getAABB(x, y, z);

        float minX, maxX, minY, maxY, minZ, maxZ;
        if (aabb != null) {
            minX = (float) aabb.minX;
            maxX = (float) aabb.maxX;
            minY = (float) aabb.minY;
            maxY = (float) aabb.maxY;
            minZ = (float) aabb.minZ;
            maxZ = (float) aabb.maxZ;
        } else {
            minX = x + 0.0f;
            maxX = x + 1.0f;
            minY = y + 0.0f;
            maxY = y + 1.0f;
            minZ = z + 0.0f;
            maxZ = z + 1.0f;
        }

        if (face == FACE_BOTTOM) {
            tessellator.vertex(minX, minY, maxZ);
            tessellator.vertex(minX, minY, minZ);
            tessellator.vertex(maxX, minY, minZ);
            tessellator.vertex(maxX, minY, maxZ);
        }
        if (face == FACE_TOP) {
            tessellator.vertex(maxX, maxY, maxZ);
            tessellator.vertex(maxX, maxY, minZ);
            tessellator.vertex(minX, maxY, minZ);
            tessellator.vertex(minX, maxY, maxZ);
        }
        if (face == FACE_NORTH) {
            tessellator.vertex(minX, maxY, minZ);
            tessellator.vertex(maxX, maxY, minZ);
            tessellator.vertex(maxX, minY, minZ);
            tessellator.vertex(minX, minY, minZ);
        }
        if (face == FACE_SOUTH) {
            tessellator.vertex(minX, maxY, maxZ);
            tessellator.vertex(minX, minY, maxZ);
            tessellator.vertex(maxX, minY, maxZ);
            tessellator.vertex(maxX, maxY, maxZ);
        }
        if (face == FACE_WEST) {
            tessellator.vertex(minX, maxY, maxZ);
            tessellator.vertex(minX, maxY, minZ);
            tessellator.vertex(minX, minY, minZ);
            tessellator.vertex(minX, minY, maxZ);
        }
        if (face == FACE_EAST) {
            tessellator.vertex(maxX, minY, maxZ);
            tessellator.vertex(maxX, minY, minZ);
            tessellator.vertex(maxX, maxY, minZ);
            tessellator.vertex(maxX, maxY, maxZ);
        }
    }
}