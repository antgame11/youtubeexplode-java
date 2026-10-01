package youtubeexplode.utils;

/** String helpers mirroring the ones used by the original library. */
public final class Strings {
    private Strings() {}

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String nullIfBlank(String s) {
        return isBlank(s) ? null : s;
    }

    /** Substring after the first occurrence of the separator; empty string if absent. */
    public static String after(String s, String sep) {
        int i = s.indexOf(sep);
        return i < 0 ? "" : s.substring(i + sep.length());
    }

    /** Substring before the first occurrence of the separator; whole string if absent. */
    public static String until(String s, String sep) {
        int i = s.indexOf(sep);
        return i < 0 ? s : s.substring(0, i);
    }

    public static String stripNonDigit(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) if (Character.isDigit(c)) sb.append(c);
        return sb.toString();
    }

    public static Long parseLong(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parseDouble(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isIdChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }
}
