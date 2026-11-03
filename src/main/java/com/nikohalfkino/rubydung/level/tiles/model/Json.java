package com.nikohalfkino.rubydung.level.tiles.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * minimal JSON reader for Minecraft/Blockbench model files
 *
 * objects become Map<String,Object> (file order), arrays List<Object>, numbers Double, plus String, Boolean and null
 */
public final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("Unexpected content after the JSON value");
        }
        return value;
    }

    private Object value() {
        if (this.pos >= this.text.length()) {
            throw error("Unexpected end of input");
        }
        char c = this.text.charAt(this.pos);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default:  return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        this.pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            this.pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Expected a string key");
            }
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, value());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                this.pos--;
                throw error("Expected ',' or '}'");
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        this.pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            this.pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(value());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                this.pos--;
                throw error("Expected ',' or ']'");
            }
        }
    }

    private String string() {
        this.pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (this.pos >= this.text.length()) {
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
                case '"':  sb.append('"');  break;
                case '\\': sb.append('\\'); break;
                case '/':  sb.append('/');  break;
                case 'b':  sb.append('\b'); break;
                case 'f':  sb.append('\f'); break;
                case 'n':  sb.append('\n'); break;
                case 'r':  sb.append('\r'); break;
                case 't':  sb.append('\t'); break;
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

    private Object literal(String word, Object value) {
        if (!this.text.startsWith(word, this.pos)) {
            throw error("Unexpected token");
        }
        this.pos += word.length();
        return value;
    }

    private Double number() {
        int start = this.pos;
        while (this.pos < this.text.length() && "+-0123456789.eE".indexOf(this.text.charAt(this.pos)) >= 0) {
            this.pos++;
        }
        if (start == this.pos) {
            throw error("Unexpected character '" + this.text.charAt(this.pos) + "'");
        }
        try {
            return Double.valueOf(this.text.substring(start, this.pos));
        } catch (NumberFormatException e) {
            this.pos = start;
            throw error("Bad number");
        }
    }

    private void skipWhitespace() {
        while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
            this.pos++;
        }
    }

    private char peek() {
        return this.pos < this.text.length() ? this.text.charAt(this.pos) : '\0';
    }

    private char next() {
        if (this.pos >= this.text.length()) {
            throw error("Unexpected end of input");
        }
        return this.text.charAt(this.pos++);
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
        return new ModelException("JSON error at line " + line + ", column " + column + ": " + message);
    }
}
