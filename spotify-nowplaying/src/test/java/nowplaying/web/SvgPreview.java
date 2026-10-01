package nowplaying.web;

import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import nowplaying.NowPlaying;

/** Writes sample cards to a folder so they can be eyeballed in a browser. Usage: SvgPreview DIR */
public final class SvgPreview {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);

        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0xff512f), 300, 300, new Color(0x1d4350)));
        g.fillRect(0, 0, 300, 300);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        String cover = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());

        NowPlaying playing = new NowPlaying("id", "Runaway (feat. Pusha T)", List.of("Kanye West", "Pusha T"), "MBDTF", 548_000, true);
        NowPlaying paused = new NowPlaying("id", "A Really Quite Extraordinarily Long Song Title That Keeps Going", List.of("Some Artist With A Long Name", "Another Artist", "Third"), "X", 200_000, false);

        Files.writeString(dir.resolve("playing.svg"), NowPlayingSvg.render(playing, 65_500, cover, NowPlayingSvg.Theme.AUTO));
        Files.writeString(dir.resolve("paused.svg"), NowPlayingSvg.render(paused, 100_000, cover, NowPlayingSvg.Theme.DARK));
        Files.writeString(dir.resolve("idle.svg"), NowPlayingSvg.render(null, 0, null, NowPlayingSvg.Theme.DARK));
        Files.writeString(dir.resolve("light.svg"), NowPlayingSvg.render(playing, 65_500, cover, NowPlayingSvg.Theme.LIGHT));
        // Short track, nearly over, to watch the digits and the bar finish
        Files.writeString(dir.resolve("ending.svg"), NowPlayingSvg.render(
                new NowPlaying("id", "Almost Over", List.of("Short Track"), "S", 62_000, true), 57_000, cover, NowPlayingSvg.Theme.DARK));
        System.out.println("wrote " + dir);
    }
}
