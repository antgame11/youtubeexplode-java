# Spotify Now Playing → YouTube Music downloader

Reads what you're playing on Spotify, finds the same song on YouTube Music (matching title, artist,
album and duration) and downloads its audio, using [youtubeexplode-java](../youtubeexplode-java).

## Setup

1. Install the library locally (once): `cd ../youtubeexplode-java && mvn install -DskipTests`
2. In the [Spotify dashboard](https://developer.spotify.com/dashboard) open your app's settings and add
   this **Redirect URI** exactly: `http://127.0.0.1:8888/callback`
3. Run (the client ID is not a secret; no client secret is needed, the app uses PKCE):

```bash
# SPOTIFY_CLIENT_ID is read from ../.env (or your environment)
./run.sh                       # download the track playing right now
./run.sh --watch               # keep running; download every new track as it starts
./run.sh --out ~/Music/spotify # choose the output folder (default: current dir)
```

The first run opens a browser for Spotify consent. The refresh token is stored in
`~/.config/youtubeexplode-nowplaying/spotify.json` (mode 600), so later runs are silent.

## Web player (listen along)

A small website that plays what you're playing on Spotify, at the same position, and follows along
when the song changes:

```bash
# SPOTIFY_CLIENT_ID is read from ../.env (or your environment)
./web.sh                      # then open http://127.0.0.1:8080 and press "Listen along"
./web.sh --offset-ms 300      # nudge sync if the web audio is consistently behind/ahead
./web.sh --host 0.0.0.0       # let other devices on your network open the page
```

How it works: the server polls Spotify every 2s, finds each track on YouTube Music, downloads the
audio into `audio-cache/` (remuxed to m4a if ffmpeg is installed) and serves it with Range support.
It also looks at Spotify's queue and prefetches the next song, so changes are usually instant. The page
polls the server once a second, seeks to Spotify's position, and corrects drift (small drift by
slightly changing playback speed, large drift by seeking). Browsers require one click before they will
play audio, hence the **Listen along** button.

Notes: Spotify keeps playing on your own device too (mute one of them). The page is not password
protected; it only binds to localhost unless you pass `--host`. Cached audio is never deleted.

## Notes

- Files are saved as `Artist, Artist - Title.<webm|mp4>`: the best audio-only stream YouTube offers
  (usually Opus in WebM). Existing files are skipped.
- A match must score above a confidence threshold (see `TrackMatcher`); otherwise the track is
  skipped with a message instead of downloading a wrong version (live, karaoke, covers...).
- Podcasts and ads are ignored.
- Tags/cover art are not written; that would need ffmpeg or a tagging library.

## Tests

```
mvn test                                              # offline (matcher + fake Spotify server)
mvn test -Dtest.excludedGroups=none -Dtest=PipelineLiveTest   # real YouTube Music lookup + download
```
