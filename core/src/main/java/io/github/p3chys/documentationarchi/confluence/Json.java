package io.github.p3chys.documentationarchi.confluence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for Confluence's REST API, which no bundle of every Archi line carries: objects are maps, arrays
 * lists, numbers doubles, and true, false and null are themselves.
 */
final class Json {

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    /** @throws IllegalArgumentException if the text is not JSON */
    static Object parse(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.space();
        if (json.at != text.length()) {
            throw json.error("end of the text");
        }
        return value;
    }

    /** The text as a JSON string, quotes included. */
    static String quote(String text) {
        StringBuilder s = new StringBuilder("\"");
        text.chars().forEach(c -> {
            if (c == '"' || c == '\\') {
                s.append('\\').append((char) c);
            } else if (c < 0x20) {
                s.append(String.format("\\u%04x", c));
            } else {
                s.append((char) c);
            }
        });
        return s.append('"').toString();
    }

    /** The map's value at the path of keys, null if a key is missing or a value on the way is not an object. */
    static Object at(Object json, String... keys) {
        Object value = json;
        for (String key : keys) {
            if (!(value instanceof Map)) {
                return null;
            }
            value = ((Map<?, ?>) value).get(key);
        }
        return value;
    }

    static String string(Object json, String... keys) {
        Object value = at(json, keys);
        if (value instanceof Double && (Double) value == Math.rint((Double) value)) {
            return Long.toString(((Double) value).longValue()); // an id sent as a number
        }
        return value == null ? null : value.toString();
    }

    static List<?> list(Object json, String... keys) {
        Object value = at(json, keys);
        return value instanceof List ? (List<?>) value : List.of();
    }

    private Object value() {
        space();
        if (at >= text.length()) {
            throw error("a value");
        }
        char c = text.charAt(at);
        switch (c) {
        case '{':
            return object();
        case '[':
            return array();
        case '"':
            return string();
        case 't':
            return word("true", Boolean.TRUE);
        case 'f':
            return word("false", Boolean.FALSE);
        case 'n':
            return word("null", null);
        default:
            return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        at++;
        space();
        if (peek('}')) {
            at++;
            return map;
        }
        while (true) {
            space();
            if (!peek('"')) {
                throw error("a key");
            }
            String key = string();
            space();
            expect(':');
            map.put(key, value());
            space();
            if (peek(',')) {
                at++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        at++;
        space();
        if (peek(']')) {
            at++;
            return list;
        }
        while (true) {
            list.add(value());
            space();
            if (peek(',')) {
                at++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        StringBuilder s = new StringBuilder();
        at++;
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') {
                return s.toString();
            }
            if (c != '\\') {
                s.append(c);
                continue;
            }
            if (at >= text.length()) {
                break;
            }
            char e = text.charAt(at++);
            switch (e) {
            case 'b':
                s.append('\b');
                break;
            case 'f':
                s.append('\f');
                break;
            case 'n':
                s.append('\n');
                break;
            case 'r':
                s.append('\r');
                break;
            case 't':
                s.append('\t');
                break;
            case 'u':
                if (at + 4 > text.length()) {
                    throw error("four hex digits");
                }
                try {
                    s.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                } catch (NumberFormatException x) {
                    throw error("four hex digits");
                }
                at += 4;
                break;
            default:
                s.append(e); // \" \\ \/
            }
        }
        throw error("the end of a string");
    }

    private Object word(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw error("a value");
        }
        at += word.length();
        return value;
    }

    private Double number() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        try {
            return Double.valueOf(text.substring(start, at));
        } catch (NumberFormatException e) {
            at = start;
            throw error("a value");
        }
    }

    private void space() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private boolean peek(char c) {
        return at < text.length() && text.charAt(at) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("'" + c + "'");
        }
        at++;
    }

    private IllegalArgumentException error(String expected) {
        return new IllegalArgumentException("Not JSON: expected " + expected + " at character " + at);
    }
}
