package de.spotifyglass;

import net.minecraft.client.gui.DrawContext;

public final class Draw {
    private Draw() { }

    /** Horizontaler Einzug einer Zeile j (von len) bei Eckenradius r. */
    public static int inset(int j, int len, int r) {
        int jj = Math.min(j, len - 1 - j);
        if (jj >= r) return 0;
        float dy = r - jj - 0.5f;
        return Math.round(r - (float) Math.sqrt(r * r - dy * dy));
    }

    public static int mix(int a, int b, float t) {
        int out = 0;
        for (int s = 0; s < 32; s += 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            out |= (((int) (ca + (cb - ca) * t)) & 255) << s;
        }
        return out;
    }

    public static int withAlpha(int rgb, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF); }

    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int j = 0; j < h; j++) {
            int in = inset(j, h, r);
            c.fill(x + in, y + j, x + w - in, y + j + 1, color);
        }
    }

    /** Liquid-Glass-Panel: Tönung, Verlaufs-Glanz oben, feiner heller Rand. */
    public static void glass(DrawContext c, int x, int y, int w, int h, int r, int tint) {
        int a = tint >>> 24, rgb = tint & 0xFFFFFF;
        r = Math.min(r, Math.min(w, h) / 2);
        for (int j = 0; j < h; j++) {
            int in = inset(j, h, r);
            float t = j / (float) (h - 1);
            float k = (1 - t) * (1 - t) * 0.22f + t * t * 0.04f;
            int fill = withAlpha(mix(rgb, 0xFFFFFF, k), a);
            int bw = 1;
            if (j < h / 2) { if (j > 0) bw = Math.max(1, inset(j - 1, h, r) - in); }
            else if (j < h - 1) bw = Math.max(1, inset(j + 1, h, r) - in);
            int x0 = x + in, x1 = x + w - in;
            int bA = Math.max(0x38, Math.min(255, a / 2 + 0x20));
            if (j == 0 || j == h - 1) {
                c.fill(x0, y + j, x1, y + j + 1, withAlpha(0xFFFFFF, j == 0 ? bA + 0x40 : bA / 2));
            } else {
                int bc = withAlpha(0xFFFFFF, bA);
                c.fill(x0, y + j, x0 + bw, y + j + 1, bc);
                c.fill(x1 - bw, y + j, x1, y + j + 1, bc);
                if (x1 - bw > x0 + bw) c.fill(x0 + bw, y + j, x1 - bw, y + j + 1, fill);
            }
        }
    }

    public static void tri(DrawContext c, int x, int y, int w, int h, boolean right, int col) {
        for (int i = 0; i < h; i++) {
            float f = 1f - Math.abs(2f * (i + 0.5f) / h - 1f);
            int len = Math.max(1, Math.round(w * f));
            if (right) c.fill(x, y + i, x + len, y + i + 1, col);
            else c.fill(x + w - len, y + i, x + w, y + i + 1, col);
        }
    }
    public static void play(DrawContext c, int cx, int cy, int col) { tri(c, cx - 4, cy - 6, 10, 12, true, col); }
    public static void pause(DrawContext c, int cx, int cy, int col) {
        rrect(c, cx - 5, cy - 6, 4, 12, 1, col); rrect(c, cx + 1, cy - 6, 4, 12, 1, col);
    }
    public static void next(DrawContext c, int cx, int cy, int col) { tri(c, cx - 6, cy - 5, 8, 10, true, col); c.fill(cx + 3, cy - 5, cx + 5, cy + 5, col); }
    public static void prev(DrawContext c, int cx, int cy, int col) { tri(c, cx - 2, cy - 5, 8, 10, false, col); c.fill(cx - 5, cy - 5, cx - 3, cy + 5, col); }

    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float h6 = h * 6; int i = (int) h6; float f = h6 - i;
        float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q;
        }
        return 0xFF000000 | ((int) (r * 255 + .5f) << 16) | ((int) (g * 255 + .5f) << 8) | (int) (b * 255 + .5f);
    }

    public static float[] toHsv(int argb) {
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) / 6f;
        else if (max == g) h = (2 + (b - r) / d) / 6f;
        else h = (4 + (r - g) / d) / 6f;
        if (h < 0) h += 1;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }
}
