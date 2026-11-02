package com.nikohalfkino.rubydung.level.tiles.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a blockstate file, /assets/rubydung/blockstates/&lt;name&gt;.toml, and bakes a model for
 * every state.
 *
 * <pre>
 * parent = "axis_column"            # optional: start from another blockstate file
 *
 * [textures]                        # defaults for every state (a state can override them)
 * side = "rubydung:pillar_side"
 * end  = "rubydung:pillar_end"
 *
 * [properties]                      # each property lists its values; the first is the default
 * axis = ["y", "z", "x"]
 *
 * [[state]]                         # the first state whose "condition" matches is used
 * condition = { axis = "z" }        # omit to match everything; a list means "any of"
 * model     = "&amp;"                   # which model; "&amp;" = the model named like this tile (the default)
 * parent    = "block/cube_column"   # optional: define that model right here, extending this one
 * textures  = { side = "other" }    # optional per-state overrides
 * transform = { rx = 90 }           # optional: rx, ry, rz (multiples of 90, x then y then z), uvlock
 * collision = [[0,0,0, 16,8,16]]    # optional: the boxes (in sixteenths) entities collide with
 * </pre>
 *
 * <h3>Collision</h3>
 * Without a {@code collision} key a state collides with its model's {@code "collision"} boxes, or
 * if the model has none, with its elements (a slab model is a half-height cube, so a slab needs
 * nothing extra). A state's boxes are written like the model's, before the transform, and turn with
 * it. {@code collision = []} makes the state walk-through.
 *
 * <h3>Implicit models</h3>
 * A state that gives a {@code parent} (or its own {@code elements}) defines its model inline, so
 * most tiles need no model file at all. The model still has an id (the {@code model} value, with
 * every "&amp;" replaced by the tile's name), and if a model file with that id exists it wins over
 * the inline definition. That is how a resource pack replaces a tile's model without touching the
 * blockstate. When a model file wins, the state's textures only fill in names the file leaves
 * undefined. Without {@code parent}/{@code elements} the model file must exist, and the textures
 * override its own.
 *
 * <p>Several states may share a model id (for instance three rotations of the same "&amp;") as long as
 * they define it identically; differing definitions need different ids.
 *
 * <p>A file with no properties and no states may instead just say {@code model = "block/cube_all"}.
 * Child files replace the parent's "properties", "state" and "model" as a whole, and merge "textures".
 * Unknown keys in a state are errors, so a typo such as "transfrom" doesn't silently do nothing.
 */
public final class BlockStateLoader {

    private static final String DIR = "/assets/rubydung/blockstates/";
    private static final int MAX_STATES = 256; // the data byte
    private static final int MAX_PARENT_DEPTH = 8;

    private static final Set<String> STATE_KEYS = new HashSet<>(
            Arrays.asList("condition", "model", "parent", "textures", "elements", "transform", "collision"));
    private static final Set<String> TRANSFORM_KEYS = new HashSet<>(
            Arrays.asList("rx", "ry", "rz", "uvlock"));

    private final ModelSource source;
    private final ModelLoader models;

    public BlockStateLoader(ModelSource source) {
        this.source = source;
        this.models = new ModelLoader(source);
    }

    /** Like {@link #load}, but never fails: problems are printed and the tile becomes a missing-texture cube. */
    public BlockStateDef loadOrMissing(String name, String tileName) {
        try {
            BlockStateDef def = load(name, tileName);
            if (def != null) {
                return def;
            }
            warn("no blockstate file " + DIR + name + ".toml; using the missing-texture cube");
        } catch (ModelException e) {
            warn("blockstate '" + name + "': " + e.getMessage());
        }
        return new BlockStateDef(Collections.<BlockStateDef.Property>emptyList(),
                new BakedModel[]{this.models.bakeMissing()});
    }

    public BlockStateDef loadOrMissing(String name) {
        return loadOrMissing(name, name);
    }

    public BlockStateDef load(String name) {
        return load(name, name);
    }

    /**
     * The baked blockstate, or null if there is no file for that name. Throws ModelException if the
     * file is bad.
     *
     * @param tileName what "&amp;" stands for in model ids
     */
    public BlockStateDef load(String name, String tileName) {
        Map<String, Object> doc = document(name, 0);
        if (doc == null) {
            return null;
        }

        List<BlockStateDef.Property> properties = parseProperties(doc.get("properties"));
        int count = 1;
        for (BlockStateDef.Property property : properties) {
            count *= property.values.size();
            if (count > MAX_STATES) {
                throw new ModelException("Too many states (more than " + MAX_STATES + "): the data byte can't hold them");
            }
        }

        Map<String, String> baseTextures = stringMap(doc.get("textures"), "textures");
        List<Map<String, Object>> entries = parseStates(doc);

        for (Map<String, Object> entry : entries) {
            checkState(entry, properties);
        }

        BakedModel[] states = new BakedModel[count];
        Map<String, BakedModel> cache = new HashMap<>();
        Map<String, String> definitions = new HashMap<>(); // model id -> its inline definition, to catch clashes

        for (int state = 0; state < count; state++) {
            Map<String, String> values = decode(properties, state);
            Map<String, Object> entry = firstMatch(entries, values);

            if (entry == null) {
                warn("blockstate '" + name + "': no state matches " + values + "; using the missing-texture cube");
                states[state] = this.models.bakeMissing();
                continue;
            }

            try {
                states[state] = bakeState(entry, tileName, baseTextures, definitions, cache);
            } catch (ModelException e) {
                warn("blockstate '" + name + "' " + values + ": " + e.getMessage());
                states[state] = this.models.bakeMissing();
            }
        }

        return new BlockStateDef(properties, states);
    }

    // -------------------------------------------------------------------------
    // File reading and parents

    private Map<String, Object> document(String name, int depth) {
        String text = this.source.read(DIR + name + ".toml");
        if (text == null) {
            return null;
        }

        Map<String, Object> doc;
        try {
            doc = Toml.parse(text);
        } catch (ModelException e) {
            throw new ModelException(name + ".toml: " + e.getMessage());
        }

        Object parent = doc.get("parent");
        if (parent instanceof String) {
            if (depth >= MAX_PARENT_DEPTH) {
                throw new ModelException("Blockstate parent chain is too deep (a loop?) at '" + name + "'");
            }
            Map<String, Object> parentDoc = document((String) parent, depth + 1);
            if (parentDoc == null) {
                throw new ModelException("Parent blockstate '" + parent + "' not found");
            }
            doc = merge(parentDoc, doc);
        }
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(Map<String, Object> parent, Map<String, Object> child) {
        Map<String, Object> result = new LinkedHashMap<>(parent);
        for (Map.Entry<String, Object> entry : child.entrySet()) {
            String key = entry.getKey();
            if (key.equals("parent")) {
                continue;
            }
            if (key.equals("textures") && entry.getValue() instanceof Map && parent.get("textures") instanceof Map) {
                Map<String, Object> textures = new LinkedHashMap<>((Map<String, Object>) parent.get("textures"));
                textures.putAll((Map<String, Object>) entry.getValue());
                result.put("textures", textures);
            } else {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Properties and states

    private static List<BlockStateDef.Property> parseProperties(Object value) {
        List<BlockStateDef.Property> properties = new ArrayList<>();
        if (value == null) {
            return properties;
        }
        if (!(value instanceof Map)) {
            throw new ModelException("[properties] must be a table");
        }

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof List) || ((List<?>) entry.getValue()).isEmpty()) {
                throw new ModelException("Property '" + name + "' needs a non-empty list of values");
            }

            List<String> values = new ArrayList<>();
            for (Object item : (List<?>) entry.getValue()) {
                String text = String.valueOf(item);
                if (values.contains(text)) {
                    throw new ModelException("Property '" + name + "' lists '" + text + "' twice");
                }
                values.add(text);
            }
            properties.add(new BlockStateDef.Property(name, values));
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseStates(Map<String, Object> doc) {
        List<Map<String, Object>> entries = new ArrayList<>();

        Object value = doc.get("state");
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (!(item instanceof Map)) {
                    throw new ModelException("Each [[state]] entry must be a table");
                }
                entries.add((Map<String, Object>) item);
            }
        } else if (value != null) {
            throw new ModelException("'state' must be written as [[state]] tables");
        }

        if (entries.isEmpty()) {
            if (!(doc.get("model") instanceof String)) {
                throw new ModelException("Needs [[state]] tables or a top-level model = \"...\"");
            }
            Map<String, Object> single = new LinkedHashMap<>();
            single.put("model", doc.get("model"));
            entries.add(single);
        }
        return entries;
    }

    /** Catches typos early: only known keys, and a condition may only name properties/values that exist. */
    private static void checkState(Map<String, Object> state, List<BlockStateDef.Property> properties) {
        for (String key : state.keySet()) {
            if (!STATE_KEYS.contains(key)) {
                throw new ModelException("Unknown key '" + key + "' in [[state]] (allowed: "
                        + new java.util.TreeSet<>(STATE_KEYS) + ")");
            }
        }
        if (state.get("model") != null && !(state.get("model") instanceof String)) {
            throw new ModelException("'model' must be a string");
        }
        if (state.get("parent") != null && !(state.get("parent") instanceof String)) {
            throw new ModelException("'parent' must be a string");
        }
        if (state.get("elements") != null && !(state.get("elements") instanceof List)) {
            throw new ModelException("'elements' must be a list of tables");
        }
        if (state.get("collision") != null) {
            ModelLoader.parseBoxes(state.get("collision"), "collision");
        }
        checkCondition(state.get("condition"), properties);
    }

    private static void checkCondition(Object condition, List<BlockStateDef.Property> properties) {
        if (condition == null) {
            return;
        }
        if (!(condition instanceof Map)) {
            throw new ModelException("'condition' must be an inline table like { axis = \"x\" }");
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) condition).entrySet()) {
            BlockStateDef.Property property = null;
            for (BlockStateDef.Property candidate : properties) {
                if (candidate.name.equals(entry.getKey())) {
                    property = candidate;
                }
            }
            if (property == null) {
                throw new ModelException("'condition' names unknown property '" + entry.getKey() + "'");
            }

            List<Object> wanted = new ArrayList<>();
            if (entry.getValue() instanceof List) {
                wanted.addAll((List<?>) entry.getValue());
            } else {
                wanted.add(entry.getValue());
            }
            for (Object item : wanted) {
                if (!property.values.contains(String.valueOf(item))) {
                    throw new ModelException("Property '" + property.name + "' has no value '" + item
                            + "' (allowed: " + property.values + ")");
                }
            }
        }
    }

    private static Map<String, String> decode(List<BlockStateDef.Property> properties, int state) {
        Map<String, String> values = new LinkedHashMap<>();
        int rest = state;
        for (BlockStateDef.Property property : properties) {
            int size = property.values.size();
            values.put(property.name, property.values.get(rest % size));
            rest /= size;
        }
        return values;
    }

    private static Map<String, Object> firstMatch(List<Map<String, Object>> entries, Map<String, String> values) {
        for (Map<String, Object> entry : entries) {
            Object condition = entry.get("condition");
            if (condition == null) {
                return entry;
            }

            boolean matches = true;
            for (Map.Entry<?, ?> required : ((Map<?, ?>) condition).entrySet()) {
                Set<String> accepted = new HashSet<>();
                if (required.getValue() instanceof List) {
                    for (Object item : (List<?>) required.getValue()) {
                        accepted.add(String.valueOf(item));
                    }
                } else {
                    accepted.add(String.valueOf(required.getValue()));
                }
                if (!accepted.contains(values.get(String.valueOf(required.getKey())))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return entry;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Baking

    private BakedModel bakeState(Map<String, Object> state, String tileName, Map<String, String> baseTextures,
                                 Map<String, String> definitions, Map<String, BakedModel> cache) {
        // "&" stands for the tile's own name, so the id is predictable and a pack can override it.
        String modelId = (state.get("model") instanceof String ? (String) state.get("model") : "&")
                .replace("&", tileName);

        Map<String, String> textures = new LinkedHashMap<>(baseTextures);
        textures.putAll(stringMap(state.get("textures"), "state textures"));

        // An inline definition: the state says what the model extends / consists of.
        Map<String, Object> definition = null;
        if (state.get("parent") != null || state.get("elements") != null) {
            definition = new LinkedHashMap<>();
            if (state.get("parent") != null) {
                definition.put("parent", state.get("parent"));
            }
            if (state.get("elements") != null) {
                definition.put("elements", state.get("elements"));
            }

            String signature = definition + "|" + textures;
            String earlier = definitions.put(modelId, signature);
            if (earlier != null && !earlier.equals(signature)) {
                throw new ModelException("Model '" + modelId + "' is defined twice with different contents;"
                        + " give the states different model ids (or share the definition)");
            }
        }

        Map<String, Object> transform = table(state.get("transform"), "transform");
        for (String key : transform.keySet()) {
            if (!TRANSFORM_KEYS.contains(key)) {
                throw new ModelException("Unknown key '" + key + "' in transform (allowed: rx, ry, rz, uvlock)");
            }
        }
        int rx = degrees(transform.get("rx"), "rx");
        int ry = degrees(transform.get("ry"), "ry");
        int rz = degrees(transform.get("rz"), "rz");
        boolean uvLock = Boolean.TRUE.equals(transform.get("uvlock"));

        String key = modelId + "|" + definition + "|" + textures + "|" + rx + "," + ry + "," + rz + "|" + uvLock;
        BakedModel cached = cache.get(key);
        if (cached == null) {
            cached = this.models.bake(modelId, definition, textures, rx, ry, rz, uvLock);
            cache.put(key, cached);
        }

        // The state's own collision boxes replace the model's, and turn with the model.
        if (state.get("collision") != null) {
            int tx = ModelLoader.turns(rx);
            int ty = ModelLoader.turns(ry);
            int tz = ModelLoader.turns(rz);
            List<float[]> boxes = new ArrayList<>();
            for (float[] box : ModelLoader.parseBoxes(state.get("collision"), "collision")) {
                boxes.add(Face.rotateBox(box, tx, ty, tz));
            }
            return cached.withCollision(boxes);
        }
        return cached;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> table(Object value, String what) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (!(value instanceof Map)) {
            throw new ModelException("'" + what + "' must be a table");
        }
        return (Map<String, Object>) value;
    }

    private static int degrees(Object value, String what) {
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number) || ((Number) value).doubleValue() != ((Number) value).intValue()) {
            throw new ModelException("'" + what + "' must be a whole number of degrees");
        }
        return ((Number) value).intValue();
    }

    private static Map<String, String> stringMap(Object value, String what) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof Map)) {
            throw new ModelException("'" + what + "' must be a table");
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static void warn(String message) {
        System.err.println("[models] " + message);
    }
}