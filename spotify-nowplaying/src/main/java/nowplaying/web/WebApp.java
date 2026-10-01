package nowplaying.web;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import nowplaying.NowPlaying;
import nowplaying.SpotifyAuth;
import nowplaying.SpotifyClient;
import youtubeexplode.YoutubeClient;

/**
 * A small website that plays whatever you are playing on Spotify, in sync: same song, same position,
 * and it follows along when the song changes.
 *
 * <pre>
 * Usage: WebApp --client-id ID [--web-port 8080] [--host 127.0.0.1] [--cache DIR]
 *               [--offset-ms 0] [--spotify-port 8888]
 * </pre>
 */
public final class WebApp {
    public static void main(String[] args) throws Exception {
        String clientId = System.getenv("SPOTIFY_CLIENT_ID");
        int webPort = 8080;
        int spotifyPort = 8888;
        String host = "127.0.0.1";
        Path cache = Path.of("audio-cache");
        long offsetMs = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--client-id" -> clientId = args[++i];
                case "--web-port" -> webPort = Integer.parseInt(args[++i]);
                case "--spotify-port" -> spotifyPort = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--cache" -> cache = Path.of(args[++i]);
                case "--offset-ms" -> offsetMs = Long.parseLong(args[++i]);
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
                }
            }
        }
        if (clientId == null || clientId.isBlank()) {
            System.err.println("Missing Spotify client ID (--client-id or SPOTIFY_CLIENT_ID). See README.");
            System.exit(1);
        }

        Path tokenFile = Path.of(System.getProperty("user.home"), ".config", "youtubeexplode-nowplaying", "spotify.json");
        SpotifyAuth auth = new SpotifyAuth(clientId, spotifyPort, tokenFile);
        System.err.println("Spotify redirect URI (must be registered in your app): " + auth.redirectUri());
        auth.accessToken(); // log in now, on the console, rather than from the poller thread

        SpotifyClient spotify = new SpotifyClient(auth);
        NowPlayingSource source = new NowPlayingSource() {
            @Override
            public Optional<NowPlaying> current() throws java.io.IOException, InterruptedException {
                return spotify.currentlyPlaying();
            }

            @Override
            public List<NowPlaying> upNext() throws java.io.IOException, InterruptedException {
                return spotify.queue();
            }
        };

        try (YoutubeClient youtube = new YoutubeClient()) {
            TrackLibrary library = new TrackLibrary(new YouTubeMusicResolver(youtube, cache), 2);
            try (Poller poller = new Poller(source, library, 2000);
                 SyncServer server = new SyncServer(poller, library, offsetMs, host, webPort)) {
                poller.start();
                server.start();
                System.out.println("Open http://" + (host.equals("0.0.0.0") ? "localhost" : host) + ":" + server.port()
                        + "/ and press \"Listen along\".");
                Thread.currentThread().join();
            } finally {
                library.shutdown();
            }
        }
    }
}
