package ua.pc.home;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/** Малює красиві сцени-фони кодом (без завантаження картинок). */
public final class SceneArt {
    private SceneArt() { }

    public static final String[] NAMES = {"dusk", "aurora", "ocean", "nebula"};

    public static Bitmap render(String name, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Random r = new Random(name.hashCode() * 31L + 7);
        switch (name) {
            case "aurora": aurora(c, w, h, r); break;
            case "ocean": ocean(c, w, h, r); break;
            case "nebula": nebula(c, w, h, r); break;
            default: dusk(c, w, h, r); break;
        }
        return b;
    }

    private static Paint fill(Shader s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(s);
        return p;
    }

    private static void stars(Canvas c, int w, int h, Random r, int count, float maxY, int tint) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < count; i++) {
            float x = r.nextFloat() * w;
            float y = r.nextFloat() * h * maxY;
            float rad = 0.5f + r.nextFloat() * (r.nextInt(12) == 0 ? 2.2f : 1.0f);
            int a = 80 + r.nextInt(170);
            p.setColor((a << 24) | (tint & 0x00FFFFFF));
            c.drawCircle(x, y, rad, p);
        }
    }

    private static Path ridge(int w, int h, float baseY, float amp, Random r) {
        float p1 = r.nextFloat() * 6.28f, p2 = r.nextFloat() * 6.28f, p3 = r.nextFloat() * 6.28f;
        float f1 = 1.2f + r.nextFloat(), f2 = 3.1f + r.nextFloat() * 2f, f3 = 8f + r.nextFloat() * 4f;
        Path path = new Path();
        path.moveTo(0, h);
        for (int x = 0; x <= w; x += 6) {
            float t = (float) x / w * 6.28f;
            float y = baseY - amp * (0.55f * (float) Math.sin(t * f1 + p1)
                    + 0.30f * (float) Math.sin(t * f2 + p2) + 0.15f * (float) Math.sin(t * f3 + p3));
            path.lineTo(x, y);
        }
        path.lineTo(w, h);
        path.close();
        return path;
    }

    private static void layers(Canvas c, int w, int h, Random r, float top, float step, int n,
                               int farColor, int nearColor, int fogColor) {
        for (int i = 0; i < n; i++) {
            float k = n == 1 ? 1f : (float) i / (n - 1);
            int col = mix(farColor, nearColor, k);
            float baseY = h * (top + step * i);
            float amp = h * (0.16f - 0.02f * i);
            Path p = ridge(w, h, baseY, amp, r);
            int dark = mix(col, 0xFF000000, 0.55f);
            c.drawPath(p, fill(new LinearGradient(0, baseY - amp, 0, h, col, dark, Shader.TileMode.CLAMP)));
            // туман між шарами
            if (i < n - 1) {
                c.drawRect(0, baseY - amp * 0.2f, w, baseY + amp * 1.6f,
                        fill(new LinearGradient(0, baseY - amp * 0.2f, 0, baseY + amp * 1.6f,
                                0x00000000, fogColor, Shader.TileMode.CLAMP)));
            }
        }
    }

    private static int mix(int a, int b, float t) {
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    // ---------------------------------------------------------------- сцени
    private static void dusk(Canvas c, int w, int h, Random r) {
        c.drawRect(0, 0, w, h, fill(new LinearGradient(0, 0, 0, h * 0.78f,
                new int[]{0xFF0A0E2E, 0xFF3A2A70, 0xFFB5527A, 0xFFFFA862},
                new float[]{0f, 0.42f, 0.78f, 1f}, Shader.TileMode.CLAMP)));
        stars(c, w, h, r, 260, 0.5f, 0xFFFFFFFF);
        c.drawCircle(w * 0.68f, h * 0.66f, h * 0.55f, fill(new RadialGradient(w * 0.68f, h * 0.66f, h * 0.55f,
                new int[]{0xCCFFE2B0, 0x66FF9A6A, 0x00FF9A6A}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP)));
        layers(c, w, h, r, 0.56f, 0.075f, 5, 0xFF6A4A98, 0xFF0C0A1C, 0x40FF9E8A);
    }

    private static void aurora(Canvas c, int w, int h, Random r) {
        c.drawRect(0, 0, w, h, fill(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF01040F, 0xFF081A36, 0xFF0B2A3C}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP)));
        stars(c, w, h, r, 320, 0.7f, 0xFFDDEEFF);
        int[] cols = {0x7700FF9C, 0x6600D4FF, 0x55A06BFF};
        for (int k = 0; k < 3; k++) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(h * (0.10f + 0.03f * k));
            p.setColor(cols[k]);
            p.setMaskFilter(new BlurMaskFilter(h * 0.05f, BlurMaskFilter.Blur.NORMAL));
            Path path = new Path();
            float ph = r.nextFloat() * 6.28f;
            float baseY = h * (0.22f + 0.1f * k);
            for (int x = 0; x <= w; x += 8) {
                float t = (float) x / w * 6.28f;
                float y = baseY + h * 0.09f * (float) Math.sin(t * 1.3f + ph) + h * 0.03f * (float) Math.sin(t * 3.7f + ph * 2);
                if (x == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            c.drawPath(path, p);
        }
        layers(c, w, h, r, 0.74f, 0.07f, 3, 0xFF0A1B2C, 0xFF02050A, 0x300A3A4A);
    }

    private static void ocean(Canvas c, int w, int h, Random r) {
        float hor = h * 0.62f;
        c.drawRect(0, 0, w, hor, fill(new LinearGradient(0, 0, 0, hor,
                new int[]{0xFF14204E, 0xFF6A3A8A, 0xFFE0607E, 0xFFFFC37A},
                new float[]{0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP)));
        stars(c, w, h, r, 120, 0.3f, 0xFFFFFFFF);
        float sx = w * 0.62f;
        c.drawCircle(sx, hor, h * 0.5f, fill(new RadialGradient(sx, hor, h * 0.5f,
                new int[]{0xDDFFE7B8, 0x55FF9A6A, 0x00FF9A6A}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP)));
        Paint sun = new Paint(Paint.ANTI_ALIAS_FLAG);
        sun.setColor(0xFFFFF1D0);
        c.drawCircle(sx, hor - h * 0.012f, h * 0.06f, sun);
        c.drawRect(0, hor, w, h, fill(new LinearGradient(0, hor, 0, h,
                new int[]{0xFF8A4A7A, 0xFF3A2A5E, 0xFF080C26}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP)));
        Paint refl = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 70; i++) {
            float t = i / 70f;
            float y = hor + 4 + t * (h - hor) * 0.95f;
            float ww = h * (0.04f + t * 0.35f) * (0.4f + r.nextFloat());
            float off = (r.nextFloat() - 0.5f) * h * 0.1f * (0.3f + t);
            refl.setColor(((int) (140 * (1f - t * 0.8f)) << 24) | 0xFFD59A);
            c.drawRect(sx + off - ww / 2, y, sx + off + ww / 2, y + 2 + t * 3, refl);
        }
        Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
        wave.setColor(0x22FFFFFF);
        for (int i = 0; i < 60; i++) {
            float y = hor + r.nextFloat() * (h - hor);
            float x = r.nextFloat() * w;
            c.drawRect(x, y, x + 20 + r.nextFloat() * 120, y + 1.5f, wave);
        }
    }

    private static void nebula(Canvas c, int w, int h, Random r) {
        c.drawRect(0, 0, w, h, fill(new LinearGradient(0, 0, w, h,
                new int[]{0xFF03030B, 0xFF0A0820, 0xFF050512}, null, Shader.TileMode.CLAMP)));
        int[] cols = {0xFF6A3BFF, 0xFF1F8BFF, 0xFFFF4FA3, 0xFF14C7B5, 0xFF8A2BE2, 0xFF2B5BFF};
        for (int i = 0; i < 7; i++) {
            float cx = r.nextFloat() * w, cy = r.nextFloat() * h, rad = w * (0.18f + r.nextFloat() * 0.30f);
            int col = cols[r.nextInt(cols.length)];
            c.drawCircle(cx, cy, rad, fill(new RadialGradient(cx, cy, rad,
                    new int[]{(0x66 << 24) | (col & 0xFFFFFF), (0x22 << 24) | (col & 0xFFFFFF), 0x00000000},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP)));
        }
        stars(c, w, h, r, 700, 1f, 0xFFFFFFFF);
        for (int i = 0; i < 14; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            c.drawCircle(x, y, 9, fill(new RadialGradient(x, y, 9,
                    new int[]{0xCCFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP)));
        }
    }
}
