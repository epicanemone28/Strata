package com.nikohalfkino.rubydung.level.tiles.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Small TOML reader covering what blockstate files need: comments, key = value (dotted keys too),
 * [tables], [[arrays of tables]], strings ("basic" and 'literal'), integers, floats, booleans,
 * arrays (multi-line is fine) and inline tables. Not supported (reported as errors): multi-line
 * strings, dates, hex/octal integers, inf/nan.
 *
 * <p>Tables become {@code Map<String,Object>} in file order, arrays {@code List<Object>},
 * integers {@code Long}, floats {@code Double}.
 */
public final class Toml {

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern FLOAT = Pattern.compile("[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");

    private final String text;
    private int pos;

    private Toml(String text) {
        this.text = text;
    }

    public static Map<String, Object> parse(String text) {
        return new Toml(text).document();
    }

    // -------------------------------------------------------------------------

    private Map<String, Object> document() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> current = root;

        while (true) {
            skipBlank();
            if (this.pos >= this.text.length()) {
                return root;
            }

            if (peek() == '[') {
                boolean arrayOfTables = peekAt(1) == '[';
                this.pos += arrayOfTables ? 2 : 1;
                List<String> path = keyPath();
                expect(']');
                if (arrayOfTables) {
                    expect(']');
                    current = openArrayTable(root, path);
                } else {
                    current = openTable(root, path);
                }
            } else {
                List<String> path = keyPath();
                skipSpaces();
                expect('=');
                skipSpaces();
                Object value = value();
                assign(current, path, value);
            }

            endOfLine();
        }
    }

    private Map<String, Object> openTable(Map<String, Object> root, List<String> path) {
        Map<String, Object> table = root;
        for (String key : path) {
            table = child(table, key);
        }
        return table;
    }

    private Map<String, Object> openArrayTable(Map<String, Object> root, List<String> path) {
        Map<String, Object> table = root;
        for (int i = 0; i < path.size() - 1; i++) {
            table = child(table, path.get(i));
        }

        String last = path.get(path.size() - 1);
        Object existing = table.get(last);
        List<Object> list;
        if (existing == null) {
            list = new ArrayList<>();
            table.put(last, list);
        } else if (existing instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) existing;
            list = cast;
        } else {
            throw error("'" + last + "' is already defined and is not an array of tables");
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        list.add(entry);
        return entry;
    }

    /** The sub-table under key (created if missing). If key is an array of tables, its last entry. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> child(Map<String, Object> table, String key) {
        Object existing = table.get(key);
        if (existing == null) {
            Map<String, Object> created = new LinkedHashMap<>();
            table.put(key, created);
            return created;
        }
        if (existing instanceof Map) {
            return (Map<String, Object>) existing;
        }
        if (existing instanceof List && !((List<Object>) existing).isEmpty()) {
            Object last = ((List<Object>) existing).get(((List<Object>) existing).size() - 1);
            if (last instanceof Map) {
                return (Map<String, Object>) last;
            }
        }
        throw error("'" + key + "' is already defined and is not a table");
    }

    private void assign(Map<String, Object> table, List<String> path, Object value) {
        Map<String, Object> target = table;
        for (int i = 0; i < path.size() - 1; i++) {
            target = child(target, path.get(i));
        }
        String key = path.get(path.size() - 1);
        if (target.containsKey(key)) {
            throw error("Duplicate key '" + key + "'");
        }
        target.put(key, value);
    }

    // -------------------------------------------------------------------------
    // Keys

    private List<String> keyPath() {
        List<String> path = new ArrayList<>();
        while (true) {
            skipSpaces();
            path.add(key());
            skipSpaces();
            if (peek() == '.') {
                this.pos++;
            } else {
                return path;
            }
        }
    }

    private String key() {
        char c = peek();
        if (c == '"') {
            return basicString();
        }
        if (c == '\'') {
            return literalString();
        }
        int start = this.pos;
        while (this.pos < this.text.length() && isBareKeyChar(this.text.charAt(this.pos))) {
            this.pos++;
        }
        if (start == this.pos) {
            throw error("Expected a key");
        }
        return this.text.substring(start, this.pos);
    }

    private static boolean isBareKeyChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    // -------------------------------------------------------------------------
    // Values

    private Object value() {
        char c = peek();
        if (c == '"') {
            if (this.text.startsWith("\"\"\"", this.pos)) {
                throw error("Multi-line strings are not supported");
            }
            return basicString();
        }
        if (c == '\'') {
            if (this.text.startsWith("'''", this.pos)) {
                throw error("Multi-line strings are not supported");
            }
            return literalString();
        }
        if (c == '[') {
            return array();
        }
        if (c == '{') {
            return inlineTable();
        }
        return scalar();
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        this.pos++; // [
        while (true) {
            skipBlank();
            if (peek() == ']') {
                this.pos++;
                return list;
            }
            list.add(value());
            skipBlank();
            if (peek() == ',') {
                this.pos++;
            } else if (peek() != ']') {
                throw error("Expected ',' or ']' in array");
            }
        }
    }

    private Map<String, Object> inlineTable() {
        Map<String, Object> table = new LinkedHashMap<>();
        this.pos++; // {
        skipSpaces();
        if (peek() == '}') {
            this.pos++;
            return table;
        }
        while (true) {
            skipSpaces();
            List<String> path = keyPath();
            skipSpaces();
            expect('=');
            skipSpaces();
            assign(table, path, value());
            skipSpaces();
            char c = peek();
            if (c == ',') {
                this.pos++;
            } else if (c == '}') {
                this.pos++;
                return table;
            } else {
                throw error("Expected ',' or '}' in inline table");
            }
        }
    }

    private Object scalar() {
        int start = this.pos;
        while (this.pos < this.text.length()) {
            char c = this.text.charAt(this.pos);
            if (c == ',' || c == ']' || c == '}' || c == '#' || Character.isWhitespace(c)) {
                break;
            }
            this.pos++;
        }
        String token = this.text.substring(start, this.pos);
        if (token.isEmpty()) {
            throw error("Expected a value");
        }
        if (token.equals("true")) {
            return Boolean.TRUE;
        }
        if (token.equals("false")) {
            return Boolean.FALSE;
        }

        String digits = token.replace("_", "");
        try {
            if (INTEGER.matcher(digits).matches()) {
                return Long.valueOf(digits);
            }
            if (FLOAT.matcher(digits).matches()) {
                return Double.valueOf(digits);
            }
        } catch (NumberFormatException e) {
            // fall through to the error below
        }
        this.pos = start;
        throw error("Unrecognised value '" + token + "' (strings need quotes)");
    }

    private String basicString() {
        this.pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (this.pos >= this.text.length() || this.text.charAt(this.pos) == '\n') {
                throw error("Unterminated string");
            }
            char c = this.text.charAt(this.pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (this.pos >= this.text.length()) {
                throw error("Unterminated escape");
            }
            char e = this.text.charAt(this.pos++);
            switch (e) {
                case 'b':  sb.append('\b'); break;
                case 't':  sb.append('\t'); break;
                case 'n':  sb.append('\n'); break;
                case 'f':  sb.append('\f'); break;
                case 'r':  sb.append('\r'); break;
                case '"':  sb.append('"');  break;
                case '\\': sb.append('\\'); break;
                case 'u':
                    if (this.pos + 4 > this.text.length()) {
                        throw error("Bad \\u escape");
                    }
                    try {
                        sb.append((char) Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("Bad \\u escape");
                    }
                    this.pos += 4;
                    break;
                default:
                    throw error("Unknown escape \\" + e);
            }
        }
    }

    private String literalString() {
        this.pos++; // opening quote
        int start = this.pos;
        while (this.pos < this.text.length() && this.text.charAt(this.pos) != '\'') {
            if (this.text.charAt(this.pos) == '\n') {
                throw error("Unterminated string");
            }
            this.pos++;
        }
        if (this.pos >= this.text.length()) {
            throw error("Unterminated string");
        }
        String value = this.text.substring(start, this.pos);
        this.pos++; // closing quote
        return value;
    }

    // -------------------------------------------------------------------------
    // Whitespace and errors

    /** Spaces and tabs only. */
    private void skipSpaces() {
        while (this.pos < this.text.length() && (this.text.charAt(this.pos) == ' ' || this.text.charAt(this.pos) == '\t')) {
            this.pos++;
        }
    }

    /** Whitespace, newlines and comments. */
    private void skipBlank() {
        while (this.pos < this.text.length()) {
            char c = this.text.charAt(this.pos);
            if (c == '#') {
                while (this.pos < this.text.length() && this.text.charAt(this.pos) != '\n') {
                    this.pos++;
                }
            } else if (Character.isWhitespace(c)) {
                this.pos++;
            } else {
                return;
            }
        }
    }

    /** After a key/value or header: only a comment may follow on the same line. */
    private void endOfLine() {
        skipSpaces();
        if (peek() == '#') {
            while (this.pos < this.text.length() && this.text.charAt(this.pos) != '\n') {
                this.pos++;
            }
        }
        if (this.pos < this.text.length() && this.text.charAt(this.pos) != '\n' && this.text.charAt(this.pos) != '\r') {
            throw error("Unexpected content at the end of the line");
        }
    }

    private char peek() {
        return peekAt(0);
    }

    private char peekAt(int offset) {
        int index = this.pos + offset;
        return index < this.text.length() ? this.text.charAt(index) : '\0';
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("Expected '" + c + "'");
        }
        this.pos++;
    }

    private ModelException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < this.pos && i < this.text.length(); i++) {
            if (this.text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new ModelException("TOML error at line " + line + ", column " + column + ": " + message);
    }
}
