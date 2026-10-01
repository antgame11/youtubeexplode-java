package nowplaying;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Settings from environment variables, falling back to a {@code .env} file. The file is looked for next to
 * where you run the program, next to the jar, and in their parent folders, so the jar works on its own.
 */
public final class Config {
    private static final Map<String, String> FILE = loadDotEnv();

    private Config() {}

    /** The value of the setting, or null if it is unset or blank. Real environment variables win. */
    public static String get(String name) {
        String env = System.getenv(name);
        if (env != null && !env.isBlank()) return env;
        String fromFile = FILE.get(name);
        return fromFile == null || fromFile.isBlank() ? null : fromFile;
    }

    private static Map<String, String> loadDotEnv() {
        Set<Path> dirs = new LinkedHashSet<>();
        Path cwd = Path.of("").toAbsolutePath();
        dirs.add(cwd);
        if (cwd.getParent() != null) dirs.add(cwd.getParent());
        try {
            Path jar = Path.of(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
            for (int i = 0; i < 3 && dir != null; i++, dir = dir.getParent()) dirs.add(dir);
        } catch (Exception ignored) {
            // Running from somewhere unusual: the working directory is still searched
        }

        Map<String, String> values = new HashMap<>();
        for (Path dir : dirs) {
            Path file = dir.resolve(".env");
            if (!Files.isRegularFile(file)) continue;
            try {
                for (String raw : Files.readAllLines(file)) {
                    String line = raw.strip();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.startsWith("export ")) line = line.substring("export ".length()).strip();
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;

                    String value = line.substring(eq + 1).strip();
                    if (value.length() >= 2
                            && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.putIfAbsent(line.substring(0, eq).strip(), value); // the first file found wins
                }
            } catch (IOException ignored) {
                // Unreadable: treat as absent
            }
        }
        return values;
    }
}
