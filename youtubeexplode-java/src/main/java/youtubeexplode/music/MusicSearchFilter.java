package youtubeexplode.music;

/** Filter applied to YouTube Music search results. */
public enum MusicSearchFilter {
    /** No filter: a mix of everything. This mode has no further pages. */
    NONE(null),
    SONGS("EgWKAQIIAWoSEAQQAxAFEAkQChAQEBUQERAO"),
    VIDEOS("EgWKAQIQAWoSEAQQAxAFEAkQChAQEBUQERAO"),
    ALBUMS("EgWKAQIYAWoSEAQQAxAFEAkQChAQEBUQERAO"),
    ARTISTS("EgWKAQIgAWoSEAQQAxAFEAkQChAQEBUQERAO");

    private final String params;

    MusicSearchFilter(String params) {
        this.params = params;
    }

    String params() {
        return params;
    }
}
