package youtubeexplode;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import youtubeexplode.bridge.ChannelPage;
import youtubeexplode.bridge.ClosedCaptionTrackResponse;
import youtubeexplode.bridge.DashManifest;
import youtubeexplode.bridge.PlayerResponse;
import youtubeexplode.bridge.PlayerSource;
import youtubeexplode.bridge.SearchResponse;
import youtubeexplode.bridge.StreamData;
import youtubeexplode.bridge.VideoWatchPage;
import youtubeexplode.bridge.cipher.CipherManifest;
import youtubeexplode.bridge.cipher.CipherOperation;
import youtubeexplode.utils.Dates;
import youtubeexplode.utils.Json;
import youtubeexplode.utils.Protobuf;
import youtubeexplode.utils.Url;

class BridgeTests {
    @Test
    void cipherOperations() {
        assertEquals("dcba", new CipherOperation.Reverse().decipher("abcd"));
        assertEquals("cd", new CipherOperation.Splice(2).decipher("abcd"));
        assertEquals("dbca", new CipherOperation.Swap(3).decipher("abcd"));

        CipherManifest manifest = new CipherManifest(
                "12345", List.of(new CipherOperation.Reverse(), new CipherOperation.Splice(1), new CipherOperation.Swap(2)));
        // abcdef -> fedcba -> edcba -> swap(0,2): cdeba
        assertEquals("cdeba", manifest.decipher("abcdef"));
    }

    @Test
    void playerSourceCipherExtraction() {
        String js = "var x={signatureTimestamp:20123};"
                + "var Xy={ab:function(a){a.reverse()},cd:function(a,b){a.splice(0,b)},ef:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c}};"
                + "foo=function(a){a=a.split(\"\");Xy.ab(a,12);Xy.cd(a,3);Xy.ef(a,5);return a.join(\"\")};";
        PlayerSource source = PlayerSource.parse(js);
        CipherManifest manifest = source.cipherManifest();
        assertNotNull(manifest);
        assertEquals("20123", manifest.signatureTimestamp());
        assertEquals(
                List.of(new CipherOperation.Reverse(), new CipherOperation.Splice(3), new CipherOperation.Swap(5)),
                manifest.operations());
    }

    @Test
    void playerSourceWithoutCipherReturnsNull() {
        assertNull(PlayerSource.parse("nothing to see here").cipherManifest());
    }

    @Test
    void jsonExtractHandlesNestedBracesAndStrings() {
        String source = "{\"a\":{\"b\":\"}\"},\"c\":[1,2]};var next = 1;";
        assertEquals("{\"a\":{\"b\":\"}\"},\"c\":[1,2]}", Json.extract(source));
    }

    @Test
    void urlHelpers() {
        assertEquals("b", Url.tryGetQueryParameter("https://x.com/p?a=1&k=b", "k"));
        assertEquals("https://x.com/p?a=1&k=v%20w", Url.setQueryParameter("https://x.com/p?a=1&k=b", "k", "v w"));
        assertEquals("https://x.com/p?a=1", Url.removeQueryParameter("https://x.com/p?a=1&k=b", "k"));
        assertEquals("https://x.com/p?k=v", Url.setQueryParameter("https://x.com/p", "k", "v"));
        assertEquals(Map.of("url", "http://a/b?c=d", "s", "sig"), Url.getQueryParameters("url=http%3A%2F%2Fa%2Fb%3Fc%3Dd&s=sig"));
    }

    @Test
    void clockDurations() {
        assertEquals(Duration.ofSeconds(65), Dates.tryParseClock("1:05"));
        assertEquals(Duration.ofSeconds(3725), Dates.tryParseClock("1:02:05"));
        assertNull(Dates.tryParseClock("75:00"));
        assertNull(Dates.tryParseClock("abc"));
    }

    @Test
    void protobufMap() {
        // field 1 (tag 0x0A) "sr", field 2 (tag 0x12) "1", wrapped in a LEN entry (tag 0x0A)
        byte[] entry = {0x0A, 2, 's', 'r', 0x12, 1, '1'};
        byte[] data = new byte[entry.length + 2];
        data[0] = 0x0A;
        data[1] = (byte) entry.length;
        System.arraycopy(entry, 0, data, 2, entry.length);

        assertEquals(Map.of("sr", "1"), Protobuf.tryDeserializeMap(data));
        assertEquals(Map.of("sr", "1"), Protobuf.tryDeserializeMap(Base64.getEncoder().encodeToString(data)));
        assertNull(Protobuf.tryDeserializeMap(new byte[] {0x08, 0x01}));
    }

    @Test
    void playerResponseStreams() {
        String xtags = Base64.getEncoder().encodeToString(new byte[] {0x0A, 7, 0x0A, 2, 's', 'r', 0x12, 1, '1'});
        String json = """
                {
                  "playabilityStatus": {"status": "OK"},
                  "videoDetails": {"title": "T", "author": "A", "channelId": "UCEnBXANsKmyj2r9xVyKoDiQ",
                                    "lengthSeconds": "61", "viewCount": "42", "keywords": ["x","y"]},
                  "streamingData": {
                    "formats": [{"itag": 18, "url": "https://v/a?clen=1000", "mimeType": "video/mp4; codecs=\\"avc1.42001E, mp4a.40.2\\"",
                                  "bitrate": 500, "qualityLabel": "360p", "fps": 30}],
                    "adaptiveFormats": [
                      {"itag": 140, "signatureCipher": "s=SIG&sp=sig&url=https%3A%2F%2Fv%2Fb", "contentLength": "2000",
                       "mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"", "bitrate": 128,
                       "audioTrack": {"id": "en.4", "displayName": "English", "audioIsDefault": true}},
                      {"itag": 137, "url": "https://v/c", "mimeType": "video/mp4; codecs=\\"avc1.640028\\"", "bitrate": 900,
                       "width": 1920, "height": 1080, "xtags": "XTAGS"}
                    ]
                  },
                  "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": [
                     {"baseUrl": "https://c", "languageCode": "en", "vssId": "a.en", "name": {"runs": [{"text": "Eng"}, {"text": "lish"}]}}]}}
                }
                """.replace("XTAGS", xtags);
        PlayerResponse r = PlayerResponse.parse(json);

        assertTrue(r.isAvailable());
        assertTrue(r.isPlayable());
        assertEquals("T", r.title());
        assertEquals(Duration.ofSeconds(61), r.duration());
        assertEquals(42L, r.viewCount());
        assertEquals(List.of("x", "y"), r.keywords());

        List<StreamData> streams = r.streams();
        assertEquals(3, streams.size());

        StreamData muxed = streams.get(0);
        assertEquals("mp4", muxed.container());
        assertEquals("avc1.42001E", muxed.videoCodec());
        assertEquals("mp4a.40.2", muxed.audioCodec());
        assertEquals(1000L, muxed.contentLength());

        StreamData audio = streams.get(1);
        assertEquals("https://v/b", audio.url());
        assertEquals("SIG", audio.signature());
        assertEquals("sig", audio.signatureParameter());
        assertNull(audio.videoCodec());
        assertEquals("mp4a.40.2", audio.audioCodec());
        assertEquals("en", audio.audioLanguageCode());
        assertEquals(Boolean.TRUE, audio.isAudioLanguageDefault());

        StreamData video = streams.get(2);
        assertEquals("avc1.640028", video.videoCodec());
        assertNull(video.audioCodec());
        assertTrue(video.isVideoUpscaled());

        PlayerResponse.ClosedCaptionTrackData track = r.closedCaptionTracks().get(0);
        assertEquals("English", track.languageName());
        assertTrue(track.isAutoGenerated());
    }

    @Test
    void playerResponseUnavailableWithoutDetails() {
        PlayerResponse r = PlayerResponse.parse("{\"playabilityStatus\":{\"status\":\"ERROR\",\"reason\":\"gone\"}}");
        assertFalse(r.isAvailable());
        assertFalse(r.isPlayable());
        assertEquals("gone", r.playabilityError());
    }

    @Test
    void dashManifest() {
        String xml = """
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" xmlns:yt="http://youtube.com/yt/2012/10/10">
                 <Period><AdaptationSet>
                  <Representation id="140" codecs="mp4a.40.2" bandwidth="130000" yt:contentLength="5000">
                    <AudioChannelConfiguration value="2"/>
                    <BaseURL>https://v/a?mime=audio%2Fmp4&amp;clen=5000</BaseURL>
                  </Representation>
                  <Representation id="137" codecs="avc1.640028" bandwidth="900000" width="1920" height="1080" frameRate="30">
                    <BaseURL>https://v/b/mime/video%2Fmp4/clen/9000</BaseURL>
                  </Representation>
                  <Representation id="rawcc" codecs="x"><BaseURL>https://v/c</BaseURL></Representation>
                  <Representation id="299" codecs="avc1"><BaseURL>https://v/d</BaseURL>
                    <SegmentList><Initialization sourceURL="https://v/sq/0"/></SegmentList></Representation>
                  <Representation id="400" codecs=""><BaseURL>https://v/e</BaseURL></Representation>
                 </AdaptationSet></Period>
                </MPD>
                """;
        List<StreamData> streams = DashManifest.parse(xml).streams();
        assertEquals(2, streams.size());

        StreamData audio = streams.get(0);
        assertEquals(140, audio.itag());
        assertEquals(5000L, audio.contentLength());
        assertEquals("mp4a.40.2", audio.audioCodec());
        assertNull(audio.videoCodec());
        assertEquals("mp4", audio.container());

        StreamData video = streams.get(1);
        assertEquals(9000L, video.contentLength());
        assertEquals("avc1.640028", video.videoCodec());
        assertEquals(1080, video.videoHeight());
        assertEquals(30, video.videoFramerate());
    }

    @Test
    void closedCaptionTrack() {
        String xml = """
                <timedtext format="3"><body>
                  <p t="1000" d="2500">Hello <s ac="0">Hel</s><s t="200" ac="0">lo</s></p>
                  <p t="5000">No duration</p>
                </body></timedtext>
                """;
        List<ClosedCaptionTrackResponse.CaptionData> captions = ClosedCaptionTrackResponse.parse(xml).captions();
        assertEquals(2, captions.size());
        assertEquals(Duration.ofMillis(1000), captions.get(0).offset());
        assertEquals(Duration.ofMillis(2500), captions.get(0).duration());
        assertEquals(2, captions.get(0).parts().size());
        assertEquals(Duration.ZERO, captions.get(0).parts().get(0).offset());
        assertEquals(Duration.ofMillis(200), captions.get(0).parts().get(1).offset());
        assertNull(captions.get(1).duration());
    }

    @Test
    void watchPageParsing() {
        String html = """
                <html><head><meta property="og:url" content="https://www.youtube.com/watch?v=abc">
                <meta itemprop="uploadDate" content="2020-05-06T07:08:09-07:00"></head>
                <body><div id="player"></div>
                <script>var ytInitialPlayerResponse = {"playabilityStatus":{"status":"OK"},"videoDetails":{"title":"Hi \\"}\\" there"}};</script>
                <script>{"x":"1,234 likes"}  "label":"5,678 likes"</script></body></html>
                """;
        VideoWatchPage page = VideoWatchPage.tryParse(html);
        assertNotNull(page);
        assertTrue(page.isAvailable());
        assertEquals(2020, page.uploadDate().getYear());
        assertEquals("Hi \"}\" there", page.playerResponse().title());
        assertEquals(1234L, page.likeCount()); // first match wins
        assertNull(VideoWatchPage.tryParse("<html><body>nothing</body></html>"));
    }

    @Test
    void channelPageParsing() {
        String html = """
                <html><head><meta property="og:url" content="https://www.youtube.com/channel/UCEnBXANsKmyj2r9xVyKoDiQ">
                <meta property="og:title" content="Chan"><meta property="og:image" content="https://i/x=s900-c"></head><body></body></html>
                """;
        ChannelPage page = ChannelPage.tryParse(html);
        assertEquals("UCEnBXANsKmyj2r9xVyKoDiQ", page.id());
        assertEquals("Chan", page.title());
        assertNull(ChannelPage.tryParse("<html></html>"));
    }

    @Test
    void searchResponseParsing() {
        String json = """
                {"contents":{"a":[{"videoRenderer":{"videoId":"abcdefghijk","title":{"runs":[{"text":"V"},{"text":"1"}]},
                  "longBylineText":{"runs":[{"text":"Au","navigationEndpoint":{"browseEndpoint":{"browseId":"UCEnBXANsKmyj2r9xVyKoDiQ"}}}]},
                  "lengthText":{"simpleText":"1:02:03"},"thumbnail":{"thumbnails":[{"url":"u","width":1,"height":2}]}}},
                  {"channelRenderer":{"channelId":"UCEnBXANsKmyj2r9xVyKoDiQ","title":{"simpleText":"C"}}},
                  {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"TOK"}}}}]}}
                """;
        SearchResponse r = SearchResponse.parse(json);
        assertEquals(1, r.videos().size());
        SearchResponse.VideoData v = r.videos().get(0);
        assertEquals("V1", v.title());
        assertEquals("Au", v.author());
        assertEquals("UCEnBXANsKmyj2r9xVyKoDiQ", v.channelId());
        assertEquals(Duration.ofSeconds(3723), v.duration());
        assertEquals(1, r.channels().size());
        assertEquals("TOK", r.continuationToken());
    }
}
