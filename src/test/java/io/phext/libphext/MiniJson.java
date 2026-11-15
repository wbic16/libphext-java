package io.phext.libphext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON parser for the test harness (the JDK has no JSON API, and
 * the fixture manifests are the only JSON the tests consume). Produces
 * {@code Map<String,Object>} / {@code List<Object>} / {@code String} /
 * {@code Long} / {@code Boolean} / {@code null}.
 */
public final class MiniJson {
    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw new IllegalArgumentException("trailing characters at " + parser.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        return (List<Object>) value;
    }

    public static String str(Map<String, Object> map, String key) {
        return (String) map.get(key);
    }

    public static long num(Map<String, Object> map, String key) {
        return (Long) map.get(key);
    }

    public static boolean bool(Map<String, Object> map, String key) {
        return (Boolean) map.get(key);
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private Object readValue() {
        skipWhitespace();
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> {
                expect("true");
                yield Boolean.TRUE;
            }
            case 'f' -> {
                expect("false");
                yield Boolean.FALSE;
            }
            case 'n' -> {
                expect("null");
                yield null;
            }
            default -> readNumber();
        };
    }

    private void expect(String literal) {
        if (!text.startsWith(literal, pos)) {
            throw new IllegalArgumentException("expected " + literal + " at " + pos);
        }
        pos += literal.length();
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (text.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            if (text.charAt(pos) != ':') {
                throw new IllegalArgumentException("expected ':' at " + pos);
            }
            pos++;
            map.put(key, readValue());
            skipWhitespace();
            char c = text.charAt(pos);
            if (c == '}') {
                pos++;
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected ',' at " + pos);
            }
            pos++;
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (text.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(readValue());
            skipWhitespace();
            char c = text.charAt(pos);
            if (c == ']') {
                pos++;
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected ',' at " + pos);
            }
            pos++;
        }
    }

    private String readString() {
        StringBuilder out = new StringBuilder();
        pos++; // opening quote
        while (true) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                char esc = text.charAt(pos++);
                switch (esc) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + esc);
                }
            } else {
                out.append(c);
            }
        }
    }

    private Long readNumber() {
        int start = pos;
        while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String raw = text.substring(start, pos);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("non-integer number in fixture manifest: " + raw, e);
        }
    }
}
