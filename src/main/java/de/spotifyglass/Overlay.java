package de.spotifyglass;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

public final class Overlay {
    public static final int W = 270, H = 68;
    static final int VX = 150, VW = 80, PX = 64, PWD = 194;
    public enum Zone { NONE, PANEL, PREV, PLAY, NEXT, VOL, SEEK }

    public static void render(DrawContext c, int mx, int my) {
        Cfg f = Cfg.I; Spotify s = Spotify.I;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Zone z = mx >= 0 ? zone(mx, my) : Zone.NONE;
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate((float) f.x, (float) f.y);
        m.scale(f.scale, f.scale);

        Draw.glass(c, 0, 0, W, H, 22, f.glass);

        if (Covers.ready) c.drawTexture(RenderPipelines.GUI_TEXTURED, Covers.ID, 10, 12, 0f, 0f, 44, 44, 128, 128, 128, 128);
        else Draw.rrect(c, 10, 12, 44, 44, 22, 0x33FFFFFF);

        c.drawText(tr, tr.trimToWidth(s.title, 194), PX, 11, 0xFFFFFFFF, false);
        c.drawText(tr, tr.trimToWidth(s.artist, 194), PX, 23, 0xB0FFFFFF, false);

        Draw.prev(c, 78, 44, z == Zone.PREV ? 0xFFFFFFFF : 0xDDFFFFFF);
        if (s.playing) Draw.pause(c, 100, 44, z == Zone.PLAY ? 0xFFFFFFFF : 0xDDFFFFFF);
        else Draw.play(c, 100, 44, z == Zone.PLAY ? 0xFFFFFFFF : 0xDDFFFFFF);
        Draw.next(c, 122, 44, z == Zone.NEXT ? 0xFFFFFFFF : 0xDDFFFFFF);

        int acc = f.accent;
        int vf = VW * s.volume / 100;
        Draw.rrect(c, VX, 43, VW, 3, 1, 0x40FFFFFF);
        Draw.rrect(c, VX, 43, Math.max(2, vf), 3, 1, acc);
        Draw.rrect(c, VX + vf - 3, 41, 7, 7, 3, 0xFFFFFFFF);
        c.drawText(tr, s.volume + "%", VX + VW + 8, 40, 0x90FFFFFF, false);

        float p = s.duration > 0 ? Math.min(1f, s.progressNow() / (float) s.duration) : 0f;
        Draw.rrect(c, PX, 57, PWD, 3, 1, 0x33FFFFFF);
        Draw.rrect(c, PX, 57, Math.max(2, (int) (PWD * p)), 3, 1, acc);
        m.popMatrix();
    }

    public static Zone zone(double mx, double my) {
        Cfg f = Cfg.I;
        double lx = (mx - f.x) / f.scale, ly = (my - f.y) / f.scale;
        if (lx < 0 || ly < 0 || lx > W || ly > H) return Zone.NONE;
        if (ly >= 34 && ly <= 54) {
            if (lx >= 67 && lx < 89) return Zone.PREV;
            if (lx >= 89 && lx < 111) return Zone.PLAY;
            if (lx >= 111 && lx < 133) return Zone.NEXT;
            if (lx >= VX - 4 && lx <= VX + VW + 4) return Zone.VOL;
        }
        if (ly > 54 && ly <= 64 && lx >= PX - 2 && lx <= PX + PWD + 2) return Zone.SEEK;
        return Zone.PANEL;
    }

    public static int volAt(double mx) {
        double lx = (mx - Cfg.I.x) / Cfg.I.scale;
        return (int) Math.round(Math.max(0, Math.min(1, (lx - VX) / VW)) * 100);
    }
    public static long seekAt(double mx) {
        double lx = (mx - Cfg.I.x) / Cfg.I.scale;
        return (long) (Math.max(0, Math.min(1, (lx - PX) / PWD)) * Spotify.I.duration);
    }
}
