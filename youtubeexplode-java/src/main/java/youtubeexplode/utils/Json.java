package youtubeexplode.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** JSON helpers. All accessors are null-safe: a missing path yields {@code null}. */
public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}

    /** Extracts the first balanced top-level JSON object from the start of the source. */
    public static String extract(String source) {
        StringBuilder buffer = new StringBuilder();
        int depth = 0;
        boolean insideString = false;

        // We trust that the source contains valid json, we just need to extract it.
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            char prev = i > 0 ? source.charAt(i - 1) : '\0';

            buffer.append(ch);

            if (ch == '"' && prev != '\\') insideString = !insideString;
            else if (ch == '{' && !insideString) depth++;
            else if (ch == '}' && !insideString) depth--;

            if (depth == 0) break;
        }

        return buffer.toString();
    }

    public static JsonNode parse(String source) {
        try {
            return MAPPER.readTree(source);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON.", e);
        }
    }

    public static JsonNode tryParse(String source) {
        try {
            JsonNode node = MAPPER.readTree(source);
            return node == null || node.isMissingNode() ? null : node;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** Encodes a value as a JSON string literal (or {@code null}). */
    public static String encode(String value) {
        if (value == null) return "null";
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String encode(Integer value) {
        return value == null ? "null" : value.toString();
    }

    // ---- Navigation ----

    /** Follows a path of property names (String) and array indexes (Integer). */
    public static JsonNode at(JsonNode node, Object... path) {
        JsonNode current = node;
        for (Object step : path) {
            if (current == null || current.isNull() || current.isMissingNode()) return null;
            current = step instanceof Integer i ? current.get(i) : current.get((String) step);
        }
        return current == null || current.isNull() || current.isMissingNode() ? null : current;
    }

    public static String str(JsonNode node, Object... path) {
        JsonNode n = at(node, path);
        return n != null && n.isTextual() ? n.asText() : null;
    }

    public static Integer integer(JsonNode node, Object... path) {
        JsonNode n = at(node, path);
        if (n == null) return null;
        if (n.isNumber()) return n.asInt();
        if (n.isTextual()) {
            try {
                return Integer.parseInt(n.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Long lng(JsonNode node, Object... path) {
        JsonNode n = at(node, path);
        if (n == null) return null;
        if (n.isNumber()) return n.asLong();
        if (n.isTextual()) {
            try {
                return Long.parseLong(n.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Boolean bool(JsonNode node, Object... path) {
        JsonNode n = at(node, path);
        return n != null && n.isBoolean() ? n.asBoolean() : null;
    }

    /** Returns array elements, or {@code null} if the node is not an array. */
    public static List<JsonNode> array(JsonNode node, Object... path) {
        JsonNode n = at(node, path);
        if (n == null || !n.isArray()) return null;
        List<JsonNode> result = new ArrayList<>(n.size());
        n.forEach(result::add);
        return result;
    }

    public static List<JsonNode> arrayOrEmpty(JsonNode node, Object... path) {
        List<JsonNode> result = array(node, path);
        return result != null ? result : List.of();
    }

    /** Concatenates the {@code text} of every run in a {@code runs} array found at the path. */
    public static String runsText(JsonNode node, Object... path) {
        List<JsonNode> runs = array(node, path);
        if (runs == null) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode run : runs) {
            String text = str(run, "text");
            if (text != null) sb.append(text);
        }
        return sb.toString();
    }

    /** Finds every value of the given property name in the tree, depth-first. */
    public static List<JsonNode> descendantProperties(JsonNode node, String propertyName) {
        List<JsonNode> result = new ArrayList<>();
        collect(node, propertyName, result);
        return result;
    }

    private static void collect(JsonNode node, String name, List<JsonNode> out) {
        if (node == null) return;
        JsonNode own = at(node, name);
        if (own != null) out.add(own);

        if (node.isArray()) {
            for (JsonNode child : node) collect(child, name, out);
        } else if (node.isObject()) {
            for (JsonNode child : node) collect(child, name, out);
        }
    }
}
