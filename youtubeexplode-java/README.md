# YoutubeExplode (Java)

A Java port of [YoutubeExplode](https://github.com/Tyrrrz/YoutubeExplode): extract metadata and media
streams from YouTube without the official API. Requires Java 17+.

Dependencies: Jackson (JSON), jsoup (HTML). HTTP uses the JDK `java.net.http.HttpClient`.

## Usage

```java
try (var youtube = new YoutubeClient()) {
    // Video metadata (accepts IDs or URLs)
    Video video = youtube.videos().get("https://youtube.com/watch?v=jNQXAC9IVRw");
    System.out.println(video.getTitle() + " by " + video.getAuthor());

    // Streams
    StreamManifest manifest = youtube.videos().streams().getManifest(video.getId());
    AudioOnlyStreamInfo audio = StreamInfo.getWithHighestBitrate(manifest.getAudioOnlyStreams());
    youtube.videos().streams().download(audio, Path.of("audio." + audio.getContainer()), p -> System.out.printf("%.0f%%%n", p * 100));

    // Closed captions (SRT)
    var captions = youtube.videos().closedCaptions();
    captions.download(captions.getManifest(video.getId()).getByLanguage("en"), Path.of("captions.srt"));

    // Playlists, channels, search: paged results are lazy streams
    youtube.playlists().getVideos("PLI5YfMzCfRtZ8eV576YoY3vIYrHjyVm_e").limit(10).forEach(System.out::println);
    youtube.channels().getByHandle(ChannelHandle.parse("@Tyrrrz"));
    youtube.search().getVideos("undefined behavior").limit(20).forEach(System.out::println);
}
```

## Logging in (cookies)

The library can sign in to Google in a real browser window and keep the cookies:

```java
var cookies = GoogleLogin.login(GoogleLogin.defaultProfileDir(), Duration.ofMinutes(5), System.err::println);
CookieStore.save(CookieStore.defaultPath(), cookies);          // mode 600
try (var youtube = YoutubeClient.withLogin(CookieStore.defaultPath())) { ... }
```

`CookieStore.load` also reads a Netscape `cookies.txt`. Treat the file like a password. **Limits (tested against
the live service):** the clients that return plain download URLs (VisionOS, Android) ignore a login, and the
clients that honor one return ciphered streams that need a JavaScript engine to decode, so a login does not
unlock downloads or get past IP blocks through this library. (The spotify-nowplaying app falls back to yt-dlp with the saved
login for that case.) Cookies and the signed `Authorization` header must not be sent
together to anything but the web clients (YouTube answers HTTP 400), so the library sends the mobile clients no
login and the TV client cookies only. Errors from the player endpoint list what every client answered.

## Differences from the C# library

- **Blocking API.** C# `async`/`await`/`CancellationToken` became plain blocking calls; run them on a
  virtual thread or executor, and interrupt the thread to cancel.
- **Paged results** (`IAsyncEnumerable`) are lazy `java.util.stream.Stream`s (or `Batches<T>` when
  you want the batches); `limit(n)` stops issuing requests.
- **ID types** (`VideoId`, `PlaylistId`, `ChannelId`, ...) have `parse` (throws
  `IllegalArgumentException`) and `tryParse` (returns `Optional`). There are no implicit conversions,
  so client methods also have `String` overloads where useful.
- `TimeSpan` → `java.time.Duration`, `DateTimeOffset` → `OffsetDateTime`, `IProgress<double>` →
  `DoubleConsumer`, `Stream` → `MediaStream` (an `InputStream` with `seek`/`length`).
- Not ported: `YoutubeExplode.Converter` (FFmpeg muxing) and the Avalonia demo GUI.

## Tests

```
mvn test                                        # offline unit tests
mvn test -Dtest.excludedGroups=none -Dtest=LiveTests   # hits the real YouTube
```
