package de.spotifyglass;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;

/** Lädt ein Bild, schneidet es rund (mit weichem Rand) und macht eine Textur daraus. */
public final class Covers {
    public static final Identifier ID = Identifier.of("spotifyglass", "cover");
    public static volatile boolean ready;
    static volatile String loadedUrl;
    static NativeImageBackedTexture tex;
    static final int N = 128;

    public static void load(String url) {
        if (url == null || url.equals(loadedUrl)) return;
        loadedUrl = url;
        Thread t = new Thread(() -> {
            try {
                byte[] b = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(),
                        HttpResponse.BodyHandlers.ofByteArray()).body();
                BufferedImage src = ImageIO.read(new ByteArrayInputStream(b));
                BufferedImage o = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = o.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int sw = src.getWidth(), sh = src.getHeight(), s = Math.min(sw, sh);
                g.drawImage(src, 0, 0, N, N, (sw - s) / 2, (sh - s) / 2, (sw + s) / 2, (sh + s) / 2, null);
                g.dispose();
                int[] px = new int[N * N];
                float c = (N - 1) / 2f, R = N / 2f;
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
                    float d = (float) Math.hypot(x - c, y - c);
                    float cov = Math.max(0f, Math.min(1f, R - d + 0.5f));
                    int p = o.getRGB(x, y);
                    px[y * N + x] = (((int) (255 * cov)) << 24) | (p & 0xFFFFFF);
                }
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    NativeImage img = new NativeImage(NativeImage.Format.RGBA, N, N, false);
                    for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) img.setColorArgb(x, y, px[y * N + x]);
                    if (tex != null) mc.getTextureManager().destroyTexture(ID);
                    tex = new NativeImageBackedTexture(() -> "spotifyglass_cover", img);
                    mc.getTextureManager().registerTexture(ID, tex);
                    tex.upload();
                    ready = true;
                });
            } catch (Exception e) { loadedUrl = null; }
        }, "spotifyglass-image");
        t.setDaemon(true);
        t.start();
    }
}
