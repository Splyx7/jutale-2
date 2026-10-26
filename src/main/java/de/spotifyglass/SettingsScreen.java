package de.spotifyglass;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

/** Einstellungen: Farbwähler (wie im Screenshot) + Overlay direkt verschieb-/bedienbar. */
public class SettingsScreen extends Screen {
    static final int PW = 190, PH = 204, CW = PW - 24;
    record Btn(int x, int y, int w, int h, Runnable run) { }
    enum Drag { NONE, SV, HUE, ALPHA, SCALE, VOL, SEEK, MOVE }

    final List<Btn> btns = new ArrayList<>();
    Drag drag = Drag.NONE;
    double ox, oy;
    int tab = 0;
    float h, s, v, a;
    boolean updating;
    TextFieldWidget hex, cid;

    public SettingsScreen() { super(Text.literal("Spotify Glass")); }

    int px() { return width - PW - 12; }
    int py() { return 12; }
    int cx() { return px() + 12; }

    @Override
    protected void init() {
        hex = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.empty());
        hex.setDrawsBackground(false); hex.setMaxLength(8); hex.setChangedListener(this::onHex);
        addDrawableChild(hex);
        cid = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.empty());
        cid.setDrawsBackground(false); cid.setMaxLength(64); cid.setText(Cfg.I.clientId);
        cid.setPlaceholder(Text.literal("Client ID einfügen"));
        cid.setChangedListener(t -> Cfg.I.clientId = t.trim());
        addDrawableChild(cid);
        loadTab(tab);
    }

    void loadTab(int t) {
        tab = t;
        if (t < 2) {
            int col = t == 0 ? Cfg.I.glass : Cfg.I.accent;
            float[] hsv = Draw.toHsv(col);
            h = hsv[0]; s = hsv[1]; v = hsv[2]; a = (col >>> 24) / 255f;
            syncHex();
        }
    }

    int colorNow() { return (Math.round(a * 255) << 24) | (Draw.hsv(h, s, v) & 0xFFFFFF); }

    void applyColor() {
        if (tab == 0) Cfg.I.glass = colorNow(); else Cfg.I.accent = colorNow();
        syncHex();
    }

    void syncHex() { updating = true; hex.setText(String.format("%08X", colorNow())); updating = false; }

    void onHex(String t) {
        if (updating || tab > 1 || !t.matches("[0-9a-fA-F]{8}")) return;
        int c = (int) Long.parseLong(t, 16);
        float[] hsv = Draw.toHsv(c);
        h = hsv[0]; s = hsv[1]; v = hsv[2]; a = (c >>> 24) / 255f;
        if (tab == 0) Cfg.I.glass = c; else Cfg.I.accent = c;
    }

    static boolean in(double mx, double my, int x, int y, int w, int hh) { return mx >= x && mx < x + w && my >= y && my < y + hh; }

    void button(DrawContext c, int mx, int my, int x, int y, int w, int hh, String label, Runnable r) {
        Draw.rrect(c, x, y, w, hh, hh / 2, in(mx, my, x, y, w, hh) ? 0x44FFFFFF : 0x26FFFFFF);
        c.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2, y + (hh - 8) / 2, 0xFFFFFFFF, false);
        btns.add(new Btn(x, y, w, hh, r));
    }

    void knob(DrawContext c, int x, int y) { Draw.rrect(c, x - 3, y - 3, 7, 16, 3, 0xFFFFFFFF); }

    @Override public void renderBackground(DrawContext c, int mx, int my, float d) { }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        btns.clear();
        int px = px(), py = py(), cx = cx();
        Draw.glass(c, px, py, PW, PH, 18, 0xB8121418);
        c.drawText(textRenderer, "Spotify Glass", cx, py + 11, 0xFFFFFFFF, false);

        String[] names = {"Glas", "Akzent", "Mehr"};
        int tw = (CW - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            int x = cx + i * (tw + 4);
            Draw.rrect(c, x, py + 26, tw, 16, 8, tab == i ? 0x55FFFFFF : 0x22FFFFFF);
            c.drawText(textRenderer, names[i], x + (tw - textRenderer.getWidth(names[i])) / 2, py + 30, 0xFFFFFFFF, false);
            btns.add(new Btn(x, py + 26, tw, 16, () -> loadTab(idx)));
        }
        hex.setVisible(tab < 2);
        cid.setVisible(tab == 2);

        if (tab < 2) {
            int sy = py + 50, sh = 64, hy = py + 120, ay = py + 136;
            for (int i = 0; i < CW; i++) {
                int in = Draw.inset(i, CW, 6);
                c.fillGradient(cx + i, sy + in, cx + i + 1, sy + sh - in, Draw.hsv(h, i / (float) (CW - 1), 1f), 0xFF000000);
            }
            int kx = cx + Math.round(s * (CW - 1)), ky = sy + Math.round((1 - v) * (sh - 1));
            Draw.rrect(c, kx - 5, ky - 5, 10, 10, 5, 0xFFFFFFFF);
            Draw.rrect(c, kx - 4, ky - 4, 8, 8, 4, Draw.hsv(h, s, v));

            for (int i = 0; i < CW; i++) {
                int in = Draw.inset(i, CW, 5);
                c.fill(cx + i, hy + in, cx + i + 1, hy + 10 - in, Draw.hsv(i / (float) (CW - 1), 1f, 1f));
            }
            knob(c, cx + Math.round(h * (CW - 1)), hy);

            int rgb = Draw.hsv(h, s, v) & 0xFFFFFF;
            for (int i = 0; i < CW; i++) {
                int in = Draw.inset(i, CW, 5);
                boolean odd = ((i / 5) & 1) == 1;
                int c1 = odd ? 0xFF5C5C5C : 0xFF8C8C8C, c2 = odd ? 0xFF8C8C8C : 0xFF5C5C5C;
                if (in < 5) c.fill(cx + i, ay + in, cx + i + 1, ay + 5, c1);
                if (in < 5) c.fill(cx + i, ay + 5, cx + i + 1, ay + 10 - in, c2);
                c.fill(cx + i, ay + in, cx + i + 1, ay + 10 - in, Draw.withAlpha(rgb, Math.round(i / (float) (CW - 1) * 255)));
            }
            knob(c, cx + Math.round(a * (CW - 1)), ay);

            c.drawText(textRenderer, "Opacity", cx, py + 152, 0xAAFFFFFF, false);
            String pct = Math.round(a * 100) + " %";
            c.drawText(textRenderer, pct, cx + CW - textRenderer.getWidth(pct), py + 152, 0xFFFFFFFF, false);
            Draw.rrect(c, cx, py + 166, CW, 18, 9, 0x22FFFFFF);
            c.drawText(textRenderer, "#", cx + 9, py + 171, 0x88FFFFFF, false);
            hex.setX(cx + 20); hex.setY(py + 170); hex.setWidth(CW - 28);
        } else {
            c.drawText(textRenderer, "Größe  " + Math.round(Cfg.I.scale * 100) + " %", cx, py + 50, 0xFFFFFFFF, false);
            float t = (Cfg.I.scale - 0.5f) / 2f;
            Draw.rrect(c, cx, py + 64, CW, 6, 3, 0x33FFFFFF);
            Draw.rrect(c, cx, py + 64, Math.max(6, Math.round(t * CW)), 6, 3, Cfg.I.accent);
            Draw.rrect(c, cx + Math.round(t * (CW - 10)), py + 62, 10, 10, 5, 0xFFFFFFFF);

            button(c, mx, my, cx, py + 78, CW, 16, "Icon: " + (Cfg.I.artistIcon ? "Künstler" : "Cover"), () -> {
                Cfg.I.artistIcon = !Cfg.I.artistIcon; Spotify.I.imgKey = null; Cfg.save(); });
            button(c, mx, my, cx, py + 98, CW, 16, "Overlay: " + (Cfg.I.visible ? "an" : "aus"), () -> {
                Cfg.I.visible = !Cfg.I.visible; Cfg.save(); });
            button(c, mx, my, cx, py + 118, CW, 16, "Position zurücksetzen", () -> {
                Cfg.I.x = 12; Cfg.I.y = 12; Cfg.save(); });

            c.drawText(textRenderer, "Spotify Client ID", cx, py + 140, 0xAAFFFFFF, false);
            Draw.rrect(c, cx, py + 150, CW, 18, 9, 0x22FFFFFF);
            cid.setX(cx + 8); cid.setY(py + 154); cid.setWidth(CW - 16);
            button(c, mx, my, cx, py + 172, CW, 16, Cfg.I.refreshToken.isEmpty() ? "Mit Spotify verbinden" : "Neu verbinden",
                    () -> Spotify.I.login());
            c.drawText(textRenderer, textRenderer.trimToWidth(Spotify.I.status, CW), cx, py + 192, 0x99FFFFFF, false);
        }

        Overlay.render(c, mx, my);
        super.render(c, mx, my, delta);
    }

    // ---------- Maus ----------
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        setFocused(null);
        double mx = click.x(), my = click.y();
        if (click.button() != 0) return false;
        for (Btn b : btns) if (in(mx, my, b.x, b.y, b.w, b.h)) { b.run.run(); return true; }

        int px = px(), py = py(), cx = cx();
        if (tab < 2) {
            if (in(mx, my, cx, py + 50, CW, 64)) { drag = Drag.SV; dragTo(mx, my); return true; }
            if (in(mx, my, cx - 4, py + 116, CW + 8, 18)) { drag = Drag.HUE; dragTo(mx, my); return true; }
            if (in(mx, my, cx - 4, py + 132, CW + 8, 18)) { drag = Drag.ALPHA; dragTo(mx, my); return true; }
        } else if (in(mx, my, cx - 4, py + 58, CW + 8, 18)) { drag = Drag.SCALE; dragTo(mx, my); return true; }

        switch (Overlay.zone(mx, my)) {
            case PREV -> Spotify.I.prev();
            case PLAY -> Spotify.I.togglePlay();
            case NEXT -> Spotify.I.next();
            case VOL -> { drag = Drag.VOL; dragTo(mx, my); }
            case SEEK -> drag = Drag.SEEK;
            case PANEL -> { drag = Drag.MOVE; ox = mx - Cfg.I.x; oy = my - Cfg.I.y; }
            default -> { return false; }
        }
        return true;
    }

    void dragTo(double mx, double my) {
        int py = py(), cx = cx();
        switch (drag) {
            case SV -> { s = clamp((mx - cx) / (CW - 1)); v = 1 - clamp((my - (py + 50)) / 63); applyColor(); }
            case HUE -> { h = Math.min(0.999f, clamp((mx - cx) / (CW - 1))); applyColor(); }
            case ALPHA -> { a = clamp((mx - cx) / (CW - 1)); applyColor(); }
            case SCALE -> Cfg.I.scale = 0.5f + 2f * clamp((mx - cx) / CW);
            case VOL -> Spotify.I.setVolume(Overlay.volAt(mx), false);
            case MOVE -> {
                Cfg.I.x = (int) Math.max(0, Math.min(width - Overlay.W * Cfg.I.scale, mx - ox));
                Cfg.I.y = (int) Math.max(0, Math.min(height - Overlay.H * Cfg.I.scale, my - oy));
            }
            default -> { }
        }
    }

    static float clamp(double d) { return (float) Math.max(0, Math.min(1, d)); }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (drag != Drag.NONE) { dragTo(click.x(), click.y()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (drag == Drag.VOL) Spotify.I.setVolume(Overlay.volAt(click.x()), true);
        if (drag == Drag.SEEK) Spotify.I.seek(Overlay.seekAt(click.x()));
        if (drag != Drag.NONE) Cfg.save();
        drag = Drag.NONE;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (Overlay.zone(mx, my) != Overlay.Zone.NONE) {
            Cfg.I.scale = Math.max(0.5f, Math.min(2.5f, Cfg.I.scale + (float) vAmt * 0.05f));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override public void removed() { Cfg.save(); }
}
