package com.nikohalfkino.rubydung.level.Tiles.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Minecraft-style block model JSON (as exported by Blockbench) and bakes it into quads.
 *
 * <p>Supported: "parent" inheritance, "textures" with "#ref" indirection, "elements" with
 * from/to, "rotation" (origin, axis, angle, rescale), "shade", and per face "uv", "texture",
 * "cullface" and "rotation". Ignored: "display", "ambientocclusion", "tintindex", "groups".
 *
 * <p>Models live at /assets/&lt;namespace&gt;/models/&lt;id&gt;.json, e.g. id "rubydung:block/cube_all"
 * (no namespace means rubydung; "minecraft:" is treated as rubydung too, for Blockbench exports).
 * Texture names are plain names under textures/block ("pillar_side"); a namespace and a "block/"
 * prefix are allowed and ignored.
 *
 * <p>A model can also be defined inline by a blockstate (see {@link BlockStateLoader}); a model file
 * with the same id then takes precedence, which is how resource packs replace it.
 */
public final class ModelLoader {

    private static final String DEFAULT_NAMESPACE = "rubydung";
    private static final String MISSING_TEXTURE = "missing";
    private static final int MAX_PARENT_DEPTH = 16;
    private static final double EPSILON = 1.0E-6;

    private final ModelSource source;
    private final Map<String, Resolved> resolved = new HashMap<>();
    private BakedModel missingCube;

    public ModelLoader(ModelSource source) {
        this.source = source;
    }

    /** A model with its parent chain merged: child textures override parent textures, child elements replace parent elements. */
    private static final class Resolved {
        final Map<String, String> textures = new LinkedHashMap<>();
        List<Object> elements = new ArrayList<>();
        /** The model's own "collision" boxes (in sixteenths), or null to derive them from the elements. */
        List<Object> collision = null;
    }

    // -------------------------------------------------------------------------
    // Public API

    /**
     * Bakes a model from a file.
     *
     * @param textureOverrides replaces entries of the model's "textures" (e.g. side -> pillar_side)
     * @param rotX, rotY, rotZ rotation of the whole model in degrees, multiples of 90 (x first, then y, then z)
     * @param uvLock           keep textures aligned to the world instead of turning with the model
     */
    public BakedModel bake(String modelId, Map<String, String> textureOverrides,
                           int rotX, int rotY, int rotZ, boolean uvLock) {
        return bake(modelId, null, textureOverrides, rotX, rotY, rotZ, uvLock);
    }

    /**
     * Bakes a model that a blockstate may define inline.
     *
     * <p>{@code definition} (nullable) holds the inline model's "parent" and/or "elements". With a
     * definition: if a model file with this id exists it wins (a resource pack replacing the model),
     * and {@code textures} only fill in names the file leaves undefined; otherwise the model is
     * built from the definition plus {@code textures}. Without a definition the file must exist and
     * {@code textures} override its entries.
     */
    public BakedModel bake(String modelId, Map<String, Object> definition, Map<String, String> textures,
                           int rotX, int rotY, int rotZ, boolean uvLock) {
        String id = qualify(modelId);
        boolean fileExists = this.source.read(modelPath(id)) != null;

        Resolved model;
        Map<String, String> overrides;
        if (definition != null && !fileExists) {
            Map<String, Object> json = new LinkedHashMap<>(definition);
            json.put("textures", new LinkedHashMap<String, Object>(textures));
            model = resolveJson(id, json, 0);
            overrides = new HashMap<>();
        } else {
            model = resolve(id, 0);
            if (definition == null) {
                overrides = textures;
            } else {
                overrides = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : textures.entrySet()) {
                    String own = model.textures.get(entry.getKey());
                    if (own == null || isPlaceholder(own)) {
                        overrides.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return bake(model, overrides, turns(rotX), turns(rotY), turns(rotZ), uvLock);
    }

    /** A plain cube with the "missing" texture on every side, used when something fails to load. */
    public BakedModel bakeMissing() {
        if (this.missingCube == null) {
            StringBuilder json = new StringBuilder(
                    "{\"textures\":{\"all\":\"missing\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{");
            for (int i = 0; i < 6; i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append('"').append(Face.NAMES[i]).append("\":{\"texture\":\"#all\",\"cullface\":\"")
                        .append(Face.NAMES[i]).append("\"}");
            }
            json.append("}}]}");

            Resolved model = resolveText("builtin/missing", json.toString(), 0);
            this.missingCube = bake(model, new HashMap<String, String>(), 0, 0, 0, false);
        }
        return this.missingCube;
    }

    // -------------------------------------------------------------------------
    // Reading and parent resolution

    private Resolved resolve(String id, int depth) {
        Resolved cached = this.resolved.get(id);
        if (cached != null) {
            return cached;
        }
        if (depth > MAX_PARENT_DEPTH) {
            throw new ModelException("Model parent chain is too deep (a loop?) at '" + id + "'");
        }

        String text = this.source.read(modelPath(id));
        if (text == null) {
            throw new ModelException("Model not found: " + modelPath(id));
        }

        Resolved model = resolveText(id, text, depth);
        this.resolved.put(id, model);
        return model;
    }

    private Resolved resolveText(String id, String text, int depth) {
        Map<String, Object> json;
        try {
            json = asObject(Json.parse(text), "model root");
        } catch (ModelException e) {
            throw new ModelException("Model '" + id + "': " + e.getMessage());
        }

        return resolveJson(id, json, depth);
    }

    /** Merges a parsed (or inline) model definition with its parent chain. */
    private Resolved resolveJson(String id, Map<String, Object> json, int depth) {
        Resolved model = new Resolved();

        Object parent = json.get("parent");
        if (parent instanceof String) {
            Resolved parentModel = resolve(qualify((String) parent), depth + 1);
            model.textures.putAll(parentModel.textures);
            model.elements = parentModel.elements;
            model.collision = parentModel.collision;
        }

        Object textures = json.get("textures");
        if (textures instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) textures).entrySet()) {
                if (entry.getValue() instanceof String) {
                    model.textures.put(String.valueOf(entry.getKey()), (String) entry.getValue());
                }
            }
        }

        Object elements = json.get("elements");
        if (elements instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) elements;
            model.elements = list;
        }

        Object collision = json.get("collision");
        if (collision instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) collision;
            model.collision = list;
        } else if (collision != null) {
            throw new ModelException("'collision' must be a list of boxes [x1, y1, z1, x2, y2, z2]");
        }

        return model;
    }

    /** "block/cube" becomes "rubydung:block/cube"; "minecraft:" is treated as the default namespace. */
    private static String qualify(String id) {
        String namespace = DEFAULT_NAMESPACE;
        int colon = id.indexOf(':');
        if (colon >= 0) {
            namespace = id.substring(0, colon);
            id = id.substring(colon + 1);
            if (namespace.equals("minecraft")) {
                namespace = DEFAULT_NAMESPACE;
            }
        }
        while (id.startsWith("/")) {
            id = id.substring(1);
        }
        return namespace + ":" + id;
    }

    /** Parent models often declare unset slots as "missing"; those count as undefined. */
    private static boolean isPlaceholder(String texture) {
        return texture.equals(MISSING_TEXTURE) || texture.endsWith(":" + MISSING_TEXTURE)
                || texture.endsWith("/" + MISSING_TEXTURE);
    }

    private static String modelPath(String qualifiedId) {
        int colon = qualifiedId.indexOf(':');
        return "/assets/" + qualifiedId.substring(0, colon) + "/models/" + qualifiedId.substring(colon + 1) + ".json";
    }

    // -------------------------------------------------------------------------
    // Baking

    private BakedModel bake(Resolved model, Map<String, String> overrides,
                            int turnsX, int turnsY, int turnsZ, boolean uvLock) {
        Map<String, String> textures = new LinkedHashMap<>(model.textures);
        textures.putAll(overrides);

        List<BakedQuad> quads = new ArrayList<>();
        for (Object element : model.elements) {
            bakeElement(asObject(element, "element"), textures, turnsX, turnsY, turnsZ, uvLock, quads);
        }

        // Entities collide with the model's "collision" boxes if it has them; otherwise with its
        // elements. Rotated elements (crossed planes, etc.) and flat ones have no volume to hit.
        List<float[]> boxes = new ArrayList<>();
        if (model.collision != null) {
            boxes.addAll(parseBoxes(model.collision, "collision"));
        } else {
            for (Object element : model.elements) {
                Map<String, Object> e = asObject(element, "element");
                Object rotation = e.get("rotation");
                if (rotation instanceof Map && Math.abs(number(((Map<?, ?>) rotation).get("angle"), "rotation.angle")) > EPSILON) {
                    continue;
                }
                double[] a = numbers(e.get("from"), 3, "element.from");
                double[] b = numbers(e.get("to"), 3, "element.to");
                float[] box = new float[6];
                boolean solid = true;
                for (int i = 0; i < 3; i++) {
                    box[i] = (float) (Math.min(a[i], b[i]) / 16.0);
                    box[i + 3] = (float) (Math.max(a[i], b[i]) / 16.0);
                    solid &= box[i + 3] - box[i] > EPSILON;
                }
                if (solid) {
                    boxes.add(box);
                }
            }
        }

        List<float[]> rotated = new ArrayList<>();
        for (float[] box : boxes) {
            rotated.add(Face.rotateBox(box, turnsX, turnsY, turnsZ));
        }
        return new BakedModel(quads, rotated);
    }

    /**
     * Reads a list of boxes [x1, y1, z1, x2, y2, z2] given in sixteenths into boxes in blocks,
     * each as {minX, minY, minZ, maxX, maxY, maxZ}. An empty list means "no collision".
     */
    static List<float[]> parseBoxes(Object value, String what) {
        if (!(value instanceof List)) {
            throw new ModelException("'" + what + "' must be a list of boxes [x1, y1, z1, x2, y2, z2]");
        }
        List<float[]> boxes = new ArrayList<>();
        for (Object item : (List<?>) value) {
            double[] n = numbers(item, 6, what + " box");
            float[] box = new float[6];
            for (int i = 0; i < 3; i++) {
                box[i] = (float) (Math.min(n[i], n[i + 3]) / 16.0);
                box[i + 3] = (float) (Math.max(n[i], n[i + 3]) / 16.0);
            }
            boxes.add(box);
        }
        return boxes;
    }

    private void bakeElement(Map<String, Object> element, Map<String, String> textures,
                             int turnsX, int turnsY, int turnsZ, boolean uvLock, List<BakedQuad> out) {
        double[] a = numbers(element.get("from"), 3, "element.from");
        double[] b = numbers(element.get("to"), 3, "element.to");
        double[] from = new double[3];
        double[] to = new double[3];
        for (int i = 0; i < 3; i++) {
            from[i] = Math.min(a[i], b[i]);
            to[i] = Math.max(a[i], b[i]);
        }

        // Optional rotation of the element itself (any angle, e.g. 45 for crossed planes).
        double[] origin = {8, 8, 8};
        int rotAxis = -1;
        double rotAngle = 0;
        boolean rescale = false;
        Object rotation = element.get("rotation");
        if (rotation instanceof Map) {
            Map<String, Object> r = asObject(rotation, "element.rotation");
            if (r.get("origin") != null) {
                origin = numbers(r.get("origin"), 3, "rotation.origin");
            }
            String axisName = String.valueOf(r.get("axis"));
            rotAxis = "x".equals(axisName) ? 0 : "y".equals(axisName) ? 1 : "z".equals(axisName) ? 2 : -1;
            if (rotAxis < 0) {
                throw new ModelException("Element rotation axis must be x, y or z, got '" + axisName + "'");
            }
            rotAngle = number(r.get("angle"), "rotation.angle");
            rescale = Boolean.TRUE.equals(r.get("rescale"));
        }

        boolean shade = !Boolean.FALSE.equals(element.get("shade"));

        Object facesObject = element.get("faces");
        if (!(facesObject instanceof Map)) {
            return;
        }
        Map<String, Object> faces = asObject(facesObject, "element.faces");

        boolean rotated = turnsX != 0 || turnsY != 0 || turnsZ != 0;

        for (int face = 0; face < 6; face++) {
            Object faceObject = faces.get(Face.NAMES[face]);
            if (faceObject == null) {
                continue;
            }
            Map<String, Object> f = asObject(faceObject, "face " + Face.NAMES[face]);

            Object textureRef = f.get("texture");
            if (!(textureRef instanceof String)) {
                throw new ModelException("Face '" + Face.NAMES[face] + "' has no texture");
            }
            String texture = resolveTexture((String) textureRef, textures);

            double[] uv = f.get("uv") != null
                    ? numbers(f.get("uv"), 4, "face.uv")
                    : defaultUv(face, from, to);
            int uvTurns = f.get("rotation") != null ? turns((int) number(f.get("rotation"), "face.rotation")) : 0;

            int modelCull = Face.byName(f.get("cullface") instanceof String ? (String) f.get("cullface") : null);

            // Corner positions in sixteenths, then the element's own rotation.
            double[][] pos = new double[4][3];
            for (int v = 0; v < 4; v++) {
                int[] corner = Face.CORNERS[face][v];
                for (int axis = 0; axis < 3; axis++) {
                    pos[v][axis] = corner[axis] == 0 ? from[axis] : to[axis];
                }
                if (rotAxis >= 0 && rotAngle != 0) {
                    rotateElementPoint(pos[v], origin, rotAxis, rotAngle, rescale);
                }
            }

            // Then the variant's 90 degree rotations, about the centre of the cell.
            if (rotated) {
                for (int v = 0; v < 4; v++) {
                    for (int axis = 0; axis < 3; axis++) {
                        pos[v][axis] -= 8;
                    }
                    Face.rotate(pos[v], turnsX, turnsY, turnsZ);
                    for (int axis = 0; axis < 3; axis++) {
                        pos[v][axis] += 8;
                    }
                }
            }

            // Which way the quad really faces (counter-clockwise winding -> outward normal).
            double[] e1 = {pos[1][0] - pos[0][0], pos[1][1] - pos[0][1], pos[1][2] - pos[0][2]};
            double[] e2 = {pos[2][0] - pos[0][0], pos[2][1] - pos[0][1], pos[2][2] - pos[0][2]};
            double nx = e1[1] * e2[2] - e1[2] * e2[1];
            double ny = e1[2] * e2[0] - e1[0] * e2[2];
            double nz = e1[0] * e2[1] - e1[1] * e2[0];
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < EPSILON) {
                continue; // zero-area face (e.g. the thin sides of a plane): nothing to draw
            }
            int lightFace = Face.fromNormal(nx, ny, nz);

            int cull = modelCull >= 0 ? Face.rotate(modelCull, turnsX, turnsY, turnsZ) : -1;

            // UVs: each vertex takes a corner of the uv rectangle, shifted by the face rotation.
            float[] uvs = new float[8];
            boolean lockable = uvLock && rotated
                    && Math.max(Math.abs(nx), Math.max(Math.abs(ny), Math.abs(nz))) > length - 1.0E-4;
            for (int v = 0; v < 4; v++) {
                double u;
                double t;
                if (lockable) {
                    double[] projected = project(lightFace, pos[v]);
                    u = projected[0];
                    t = projected[1];
                } else {
                    int corner = (v + uvTurns) % 4;
                    u = (corner == 0 || corner == 1) ? uv[0] : uv[2];
                    t = (corner == 0 || corner == 3) ? uv[1] : uv[3];
                }
                uvs[v * 2] = (float) u;
                uvs[v * 2 + 1] = (float) t;
            }

            float[] positions = new float[12];
            for (int v = 0; v < 4; v++) {
                for (int axis = 0; axis < 3; axis++) {
                    positions[v * 3 + axis] = (float) (pos[v][axis] / 16.0);
                }
            }

            out.add(new BakedQuad(positions, uvs, texture, cull, modelCull, lightFace, shade));
        }
    }

    /** Default uv rectangle {u1, v1, u2, v2} of a face, from the element's extent (Minecraft's rule). */
    private static double[] defaultUv(int face, double[] f, double[] t) {
        switch (face) {
            case Face.DOWN:  return new double[]{f[0], 16 - t[2], t[0], 16 - f[2]};
            case Face.UP:    return new double[]{f[0], f[2], t[0], t[2]};
            case Face.NEG_Z: return new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case Face.POS_Z: return new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
            case Face.WEST:  return new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
            default:         return new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        }
    }

    /** World-aligned uv of a point (in sixteenths) on a face looking toward {@code face}: used by uvlock. */
    private static double[] project(int face, double[] p) {
        switch (face) {
            case Face.UP:    return new double[]{p[0], p[2]};
            case Face.DOWN:  return new double[]{p[0], 16 - p[2]};
            case Face.NEG_Z: return new double[]{16 - p[0], 16 - p[1]};
            case Face.POS_Z: return new double[]{p[0], 16 - p[1]};
            case Face.WEST:  return new double[]{p[2], 16 - p[1]};
            default:         return new double[]{16 - p[2], 16 - p[1]};
        }
    }

    /** Rotates a point by any angle about an axis through origin (same sense as the 90 degree turns). */
    private static void rotateElementPoint(double[] p, double[] origin, int axis, double degrees, boolean rescale) {
        double radians = Math.toRadians(degrees);
        double c = Math.cos(radians);
        double s = Math.sin(radians);

        double x = p[0] - origin[0];
        double y = p[1] - origin[1];
        double z = p[2] - origin[2];

        if (rescale && Math.abs(c) > EPSILON) {
            double k = 1.0 / Math.abs(c);
            if (axis != 0) x *= k;
            if (axis != 1) y *= k;
            if (axis != 2) z *= k;
        }

        double nx = x;
        double ny = y;
        double nz = z;
        switch (axis) {
            case 0:  ny = y * c - z * s; nz = y * s + z * c;  break;
            case 1:  nx = x * c + z * s; nz = -x * s + z * c; break;
            default: nx = x * c + y * s; ny = -x * s + y * c; break;
        }

        p[0] = nx + origin[0];
        p[1] = ny + origin[1];
        p[2] = nz + origin[2];
    }

    // -------------------------------------------------------------------------
    // Helpers

    /** "#side" -> whatever "side" maps to (following chains); a plain name is used as is. */
    private static String resolveTexture(String reference, Map<String, String> textures) {
        String current = reference;
        for (int i = 0; i < MAX_PARENT_DEPTH && current != null && current.startsWith("#"); i++) {
            current = textures.get(current.substring(1));
        }
        if (current == null || current.startsWith("#")) {
            return MISSING_TEXTURE;
        }

        int colon = current.indexOf(':');
        if (colon >= 0) {
            current = current.substring(colon + 1);
        }
        if (current.startsWith("block/")) {
            current = current.substring("block/".length());
        }
        return current;
    }

    static int turns(int degrees) {
        if (degrees % 90 != 0) {
            throw new ModelException("Rotation must be a multiple of 90 degrees, got " + degrees);
        }
        return ((degrees / 90) % 4 + 4) % 4;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String what) {
        if (!(value instanceof Map)) {
            throw new ModelException("Expected an object for " + what);
        }
        return (Map<String, Object>) value;
    }

    private static double number(Object value, String what) {
        if (!(value instanceof Number)) {
            throw new ModelException("Expected a number for " + what);
        }
        return ((Number) value).doubleValue();
    }

    private static double[] numbers(Object value, int count, String what) {
        if (!(value instanceof List) || ((List<?>) value).size() != count) {
            throw new ModelException("Expected a list of " + count + " numbers for " + what);
        }
        double[] result = new double[count];
        for (int i = 0; i < count; i++) {
            result[i] = number(((List<?>) value).get(i), what);
        }
        return result;
    }
}