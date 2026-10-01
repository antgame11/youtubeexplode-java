package nowplaying;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import youtubeexplode.music.MusicRef;
import youtubeexplode.music.MusicSearchResult.Song;

/** Picks the YouTube Music song that best corresponds to a Spotify track. */
public final class TrackMatcher {
    /** Scores at or above this are considered a confident match. */
    public static final double CONFIDENT = 6.0;

    private static final List<String> VERSION_WORDS =
            List.of("live", "remix", "karaoke", "instrumental", "cover", "slowed", "sped up", "reverb", "acoustic", "demo", "8d", "nightcore");

    public record Match(Song song, double score) {
        public boolean isConfident() {
            return score >= CONFIDENT;
        }
    }

    private TrackMatcher() {}

    public static Optional<Match> best(NowPlaying track, List<Song> candidates) {
        return candidates.stream()
                .map(s -> new Match(s, score(track, s)))
                .max(Comparator.comparingDouble(Match::score));
    }

    static double score(NowPlaying track, Song song) {
        double score = 0;

        // Title: compare with "(feat. X)", "[Remastered]" and "- Live" style suffixes removed
        String t1 = baseTitle(track.title());
        String t2 = baseTitle(song.title());
        if (t1.equals(t2)) score += 3;
        else if (t1.contains(t2) || t2.contains(t1)) score += 1.5;
        else score += 2 * jaccard(t1, t2);

        // Artist: any Spotify artist matches any YouTube Music artist
        double artistScore = 0;
        for (String spotifyArtist : track.artists()) {
            for (MusicRef ytArtist : song.artists()) {
                String a = normalize(spotifyArtist);
                String b = normalize(ytArtist.name());
                if (a.equals(b)) artistScore = Math.max(artistScore, 3);
                else if (a.contains(b) || b.contains(a)) artistScore = Math.max(artistScore, 1.5);
            }
        }
        score += artistScore;

        // Duration
        if (song.duration() != null) {
            long diffSeconds = Math.abs(song.duration().toMillis() - track.durationMs()) / 1000;
            if (diffSeconds <= 2) score += 3;
            else if (diffSeconds <= 5) score += 1.5;
            else if (diffSeconds > 15) score -= 2;
        }

        // Album, when both are known
        if (track.album() != null && song.album() != null && normalize(track.album()).equals(normalize(song.album().name()))) {
            score += 1;
        }

        // Alternate versions that the Spotify track is not
        String spotifyTitle = normalize(track.title());
        String ytTitle = normalize(song.title());
        for (String word : VERSION_WORDS) {
            if (containsWord(ytTitle, word) && !containsWord(spotifyTitle, word)) score -= 3;
        }

        return score;
    }

    static String baseTitle(String title) {
        String s = title.replaceAll("\\(.*?\\)|\\[.*?]", " ");
        int dash = s.indexOf(" - ");
        if (dash > 0) s = s.substring(0, dash);
        String n = normalize(s);
        return n.isEmpty() ? normalize(title) : n;
    }

    static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static boolean containsWord(String haystack, String word) {
        return (" " + haystack + " ").contains(" " + word + " ");
    }

    private static double jaccard(String a, String b) {
        Set<String> sa = new HashSet<>(List.of(a.split(" ")));
        Set<String> sb = new HashSet<>(List.of(b.split(" ")));
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        sa.retainAll(sb);
        return union.isEmpty() ? 0 : (double) sa.size() / union.size();
    }
}
