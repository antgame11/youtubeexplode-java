package youtubeexplode.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Minimal protobuf reader, sufficient for {@code map<string, string>} payloads. */
public final class Protobuf {
    private Protobuf() {}

    private static boolean isLenField(long tag) {
        return (tag & 0x7) == 2;
    }

    private record Varint(long value, int next) {}

    private static Varint readVarint(byte[] data, int i) {
        long value = 0;
        int shift = 0;
        while (i < data.length) {
            int b = data[i++] & 0xFF;
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return new Varint(value, i);
            shift += 7;
            if (shift >= 64) break;
        }
        return null;
    }

    /**
     * Deserializes a protobuf-encoded map&lt;string, string&gt;. Each top-level LEN field is a map entry
     * where field 1 is the key and field 2 is the value. Returns null if the data cannot be parsed.
     */
    public static Map<String, String> tryDeserializeMap(byte[] data) {
        Map<String, String> result = new HashMap<>();

        int i = 0;
        while (i < data.length) {
            Varint outerTag = readVarint(data, i);
            if (outerTag == null) return null;
            i = outerTag.next();
            if (!isLenField(outerTag.value())) return null;

            Varint entryLen = readVarint(data, i);
            if (entryLen == null) return null;
            i = entryLen.next();

            long end = i + entryLen.value();
            if (entryLen.value() < 0 || end > data.length) return null;
            int entryEnd = (int) end;

            String key = null;
            String value = null;
            int j = i;
            while (j < entryEnd) {
                Varint fieldTag = readVarint(data, j);
                if (fieldTag == null) break;
                j = fieldTag.next();
                if (!isLenField(fieldTag.value())) break;
                int fieldNum = (int) (fieldTag.value() >>> 3);

                Varint len = readVarint(data, j);
                if (len == null) break;
                j = len.next();
                if (len.value() < 0 || j + len.value() > data.length) break;
                String str = new String(data, j, (int) len.value(), StandardCharsets.UTF_8);
                j += (int) len.value();

                if (fieldNum == 1) key = str;
                else if (fieldNum == 2) value = str;
            }

            if (key != null) result.put(key, value);
            i = entryEnd;
        }

        return result;
    }

    public static Map<String, String> tryDeserializeMap(String base64) {
        try {
            String normalized = base64.replace('-', '+').replace('_', '/');
            return tryDeserializeMap(Base64.getDecoder().decode(normalized));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
