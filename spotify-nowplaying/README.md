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
./web.sh --card-only          # public-safe: only the now-playing card/embed, no audio
```

How it works: the server polls Spotify every 2s, finds each track on YouTube Music, downloads the
audio into `audio-cache/` (remuxed to m4a if ffmpeg is installed) and serves it with Range support.
It also looks at Spotify's queue and prefetches the next song, so changes are usually instant. The page
polls the server once a second, seeks to Spotify's position, and corrects drift (small drift by
slightly changing playback speed, large drift by seeking). Browsers require one click before they will
play audio, hence the **Listen along** button.

Notes: Spotify keeps playing on your own device too (mute one of them). The page is not password
protected; it only binds to localhost unless you pass `--host`. Cached audio is never deleted.

## Embeds: widget and GitHub profile card

The same server also serves two display-only views of what you're playing:

| URL | What |
|---|---|
| `/embed` | A compact card for an `<iframe>` on any web page. `?theme=dark\|light`, `?bg=transparent` |
| `/now.svg` | A card as a standalone SVG, made for a GitHub profile README. `?theme=dark\|light` |

```html
<iframe src="http://localhost:8080/embed" width="440" height="112" style="border:0" loading="lazy"></iframe>
```

**GitHub profile card.** Put this in your profile README (`<user>/<user>` repo):

```markdown
[![Now playing on Spotify](https://YOUR-PUBLIC-HOST/now.svg)](https://open.spotify.com)
```

How it works: GitHub only displays images, it strips scripts, and its image proxy blocks external
resources. So `/now.svg` is pure SVG with inline styles, the album cover embedded as base64, and the
animation done with SVG's built-in SMIL (the progress bar fills over the remaining time, the elapsed
time digits roll over every second, and the equalizer bounces while playing). Each time someone loads
the page, the server generates a fresh card starting at the current position, and the bar then runs
by itself until the song ends. It can't update *after* loading (no scripts), so a page refresh
fetches the new song. The response is sent with `Cache-Control: no-cache`, but GitHub's image proxy
decides how fresh it really is, so it may lag by a bit.

GitHub's servers must be able to reach it, so `localhost` won't work. Either run it on a server
you control, or use a tunnel (for example `cloudflared tunnel --url http://localhost:8080`, which prints
a public https URL). **Use card-only mode when it's public:**

```bash
./web.sh --card-only    # serves only /now.svg, /embed and /api/now; no audio is downloaded or served
```

Without `--card-only` the public URL would also expose `/audio/...` (the music files) to anyone.
Card-only mode shows just the title, artist and cover of what you're playing, and needs no YouTube
access at all.

To preview the card styles offline: `mvn -q test-compile` and then run
`nowplaying.web.SvgPreview <dir>`, which writes sample `.svg` files you can open in a browser.

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
