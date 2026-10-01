package youtubeexplode.search;

/** Filter applied to search results. */
public enum SearchFilter {
    /** No filter; all kinds of results are returned. */
    NONE,
    /** Only videos. */
    VIDEO,
    /** Only playlists. */
    PLAYLIST,
    /** Only channels. */
    CHANNEL
}
