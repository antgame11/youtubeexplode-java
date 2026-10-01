package youtubeexplode.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.bridge.cipher.CipherManifest;
import youtubeexplode.bridge.cipher.CipherOperation;

/** The player's base.js, used to extract the signature cipher. */
public final class PlayerSource {
    private static final Pattern SIGNATURE_TIMESTAMP = Pattern.compile("(?:signatureTimestamp|sts):(\\d{5})");
    private static final Pattern CIPHER_CALLSITE = Pattern.compile(
            "[$_\\w]+=function\\([$_\\w]+\\)\\{([$_\\w]+)=\\1\\.split\\(['\"]{2}\\);.*?return \\1\\.join\\(['\"]{2}\\)\\}",
            Pattern.DOTALL);
    private static final Pattern CIPHER_CONTAINER = Pattern.compile("([$_\\w]+)\\.[$_\\w]+\\([$_\\w]+,\\d+\\);");
    private static final Pattern SWAP_FUNC =
            Pattern.compile("([$_\\w]+):function\\([$_\\w]+,[$_\\w]+\\)\\{+[^}]*?%[^}]*?\\}", Pattern.DOTALL);
    private static final Pattern SPLICE_FUNC =
            Pattern.compile("([$_\\w]+):function\\([$_\\w]+,[$_\\w]+\\)\\{+[^}]*?splice[^}]*?\\}", Pattern.DOTALL);
    private static final Pattern REVERSE_FUNC =
            Pattern.compile("([$_\\w]+):function\\([$_\\w]+\\)\\{+[^}]*?reverse[^}]*?\\}", Pattern.DOTALL);
    private static final Pattern CALL = Pattern.compile("[$_\\w]+\\.([$_\\w]+)\\([$_\\w]+,\\d+\\)");
    private static final Pattern CALL_INDEX = Pattern.compile("\\([$_\\w]+,(\\d+)\\)");

    private final String content;
    private CipherManifest cipherManifest;
    private boolean cipherResolved;

    public PlayerSource(String content) {
        this.content = content;
    }

    public static PlayerSource parse(String raw) {
        return new PlayerSource(raw);
    }

    private static String group(Pattern pattern, String input, int group) {
        Matcher m = pattern.matcher(input);
        if (!m.find()) return null;
        String value = m.group(group);
        return value == null || value.isBlank() ? null : value;
    }

    /** Returns the cipher manifest, or null if it could not be extracted. */
    public synchronized CipherManifest cipherManifest() {
        if (!cipherResolved) {
            cipherManifest = extractCipherManifest();
            cipherResolved = true;
        }
        return cipherManifest;
    }

    private CipherManifest extractCipherManifest() {
        String signatureTimestamp = group(SIGNATURE_TIMESTAMP, content, 1);
        if (signatureTimestamp == null) return null;

        // Find where the player calls the cipher functions
        String callsite = group(CIPHER_CALLSITE, content, 0);
        if (callsite == null) return null;

        // Find the object that defines the cipher functions
        String containerName = group(CIPHER_CONTAINER, callsite, 1);
        if (containerName == null) return null;

        // Find the definition of the cipher functions
        String definition = group(
                Pattern.compile("var " + Pattern.quote(containerName) + "=\\{.*?\\};", Pattern.DOTALL), content, 0);
        if (definition == null) return null;

        String swapName = group(SWAP_FUNC, definition, 1);
        String spliceName = group(SPLICE_FUNC, definition, 1);
        String reverseName = group(REVERSE_FUNC, definition, 1);

        List<CipherOperation> operations = new ArrayList<>();
        for (String statement : callsite.split(";")) {
            String called = group(CALL, statement, 1);
            if (called == null) continue;

            if (called.equals(swapName)) {
                operations.add(new CipherOperation.Swap(Integer.parseInt(group(CALL_INDEX, statement, 1))));
            } else if (called.equals(spliceName)) {
                operations.add(new CipherOperation.Splice(Integer.parseInt(group(CALL_INDEX, statement, 1))));
            } else if (called.equals(reverseName)) {
                operations.add(new CipherOperation.Reverse());
            }
        }

        return new CipherManifest(signatureTimestamp, operations);
    }
}
