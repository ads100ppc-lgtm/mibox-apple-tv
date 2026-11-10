package ua.pc.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Матове скло: під панеллю малюється розмита копія поточного фону (того, що справді за нею),
 * з підвищеною насиченістю, легким затемненням, світлим відблиском і тонкою кромкою.
 * Розмиття робиться один раз на малій копії, тому нічого не навантажує.
 */
public final class Frost extends Drawable {
    private static volatile Bitmap backdrop;
    private static int screenW = 1920, screenH = 1080;

    public static void setScreen(int w, int h) { screenW = w; screenH = h; }

    /** Викликати при кожній зміні фону. */
    public static void update(Bitmap src) {
        if (src == null) return;
        try {
            final int w = 96, h = 54;
            Bitmap s = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
            int[] px = new int[w * h];
            s.getPixels(px, 0, w, 0, 0, w, h);
            for (int i = 0; i < 3; i++) { box(px, w, h, 4, true); box(px, w, h, 4, false); }
            s.setPixels(px, 0, w, 0, 0, w, h);
            backdrop = s;
        } catch (Throwable ignored) { }
    }

    private static void box(int[] px, int w, int h, int r, boolean horizontal) {
        int[] out = new int[px.length];
        int n = 2 * r + 1;
        int outer = horizontal ? h : w, inner = horizontal ? w : h;
        for (int o = 0; o < outer; o++) {
            for (int i = 0; i < inner; i++) {
                int a = 0, rr = 0, g = 0, b = 0;
                for (int k = -r; k <= r; k++) {
                    int ii = Math.max(0, Math.min(inner - 1, i + k));
                    int p = px[horizontal ? o * w + ii : ii * w + o];
                    rr += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255;
                }
                out[horizontal ? o * w + i : i * w + o] = 0xFF000000 | ((rr / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
        System.arraycopy(out, 0, px, 0, px.length);
    }

    private final View host;
    private final float radius;
    private final int darkTint;
    private final Paint bm = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final RectF dst = new RectF();
    private final int[] loc = new int[2];

    public Frost(View host, float radiusPx, int darkTint) {
        this.host = host;
        this.radius = radiusPx;
        this.darkTint = darkTint;
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.5f);
        bm.setColorFilter(new ColorMatrixColorFilter(cm));
        line.setStyle(Paint.Style.STROKE);
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        rf.set(b);
        path.reset();
        path.addRoundRect(rf, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(path);
        Bitmap back = backdrop;
        if (back != null) {
            // позиція за розміткою (без анімаційних зсувів/масштабу): готовий малюнок не перераховується під час анімації
            int x = 0, y = 0;
            View rv = host;
            for (View v = host; v != null; ) {
                x += v.getLeft();
                y += v.getTop();
                rv = v;
                android.view.ViewParent pp = v.getParent();
                v = pp instanceof View ? (View) pp : null;
            }
            int rw = rv.getWidth() > 0 ? rv.getWidth() : screenW, rh = rv.getHeight() > 0 ? rv.getHeight() : screenH;
            x -= rv.getLeft();
            y -= rv.getTop();
            dst.set(-x + b.left, -y + b.top, -x + b.left + rw, -y + b.top + rh);
            c.drawBitmap(back, null, dst, bm);
        }
        fill.setShader(null);
        fill.setColor(darkTint);
        c.drawRect(rf, fill);
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0x44FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(rf, fill);
        c.restore();
        // тонка кромка: яскравіша зверху, як у склі, що ловить світло
        float sw = Math.max(1f, radius / 28f);
        line.setStrokeWidth(sw);
        line.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xAAFFFFFF, 0x22FFFFFF, Shader.TileMode.CLAMP));
        rf.inset(sw / 2, sw / 2);
        c.drawRoundRect(rf, radius, radius, line);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
