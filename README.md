# YouTube × Spotify toolkit (Java)

Two Java projects:

| Project | What it is |
|---|---|
| [`youtubeexplode-java/`](youtubeexplode-java) | A Java port of [YoutubeExplode](https://github.com/Tyrrrz/YoutubeExplode): video/playlist/channel/search metadata, stream manifests, audio/video downloads, closed captions, and YouTube Music search. No API key needed. |
| [`spotify-nowplaying/`](spotify-nowplaying) | An app built on the library. Reads what's playing on Spotify, finds it on YouTube Music and downloads it, or serves a small website that plays it in sync with Spotify ("listen along"). |

Requires Java 17+ and Maven. ffmpeg is optional (the web player uses it to remux audio).

## Quick start

```bash
# 1. Build everything (library first, then the app jar). Re-run after pulling changes.
./build.sh                    # add --skip-tests to go faster

# 2. Configure Spotify
cp .env.example .env          # then put your client ID in .env
#    In the Spotify dashboard, add this redirect URI to your app:
#    http://127.0.0.1:8888/callback

# 3. Run (stop and restart a running instance after rebuilding: the old code stays loaded in memory)
cd spotify-nowplaying
./run.sh                      # download the song playing right now
./run.sh --watch              # download every new song as it starts
./web.sh                      # web player at http://127.0.0.1:8080
./web.sh --card-only          # just the now-playing card: /now.svg (GitHub profile) and /embed
```

If YouTube answers "video not available" on that machine (common on servers), sign in once with
`./run.sh login`; see [spotify-nowplaying/README.md](spotify-nowplaying/README.md#signing-in-to-youtube-fixes-video-not-available-on-some-networks).

`mvn package` makes one self-contained jar, so you can also just run `java -jar` on it anywhere.
The app reads `.env` itself (next to the jar or in the folder you run it from). The first run opens a browser for Spotify
consent; afterwards the refresh token is kept in `~/.config/youtubeexplode-nowplaying/`.

## Configuration

`.env` (git-ignored):

| Variable | Meaning |
|---|---|
| `SPOTIFY_CLIENT_ID` | Client ID of your Spotify app. Not secret. No client secret is needed (PKCE login). |
| `YOUTUBE_COOKIES_FILE` | Optional. Where the saved Google login is stored (default `~/.config/youtubeexplode/cookies.json`). |

## Tests

```bash
(cd youtubeexplode-java && mvn test)     # offline unit tests
(cd spotify-nowplaying  && mvn test)     # offline: matcher, fake Spotify server, web server
# Live tests hit the real YouTube / YouTube Music:
(cd youtubeexplode-java && mvn test -Dtest.excludedGroups=none -Dtest=LiveTests)
(cd spotify-nowplaying  && mvn test -Dtest.excludedGroups=none -Dtest=PipelineLiveTest)
```

## What is not in git

See [.gitignore](.gitignore): `.env`, browser captures (`*.har`), build output, downloaded media and
`audio-cache/`, and the upstream C# `youtubeexplode/` checkout.

## Disclaimer

This talks to YouTube's undocumented internal APIs, which can change without notice, and downloading
or re-streaming content may violate YouTube's Terms of Service and copyright law. Use it for personal
purposes and at your own risk.
