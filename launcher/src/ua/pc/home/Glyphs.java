package ua.pc.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Значки в манері SF Symbols: однакова товщина лінії, круглі кінці, чіткі пропорції. */
public final class Glyphs {
    private Glyphs() { }

    private static final RectF R = new RectF();

    /** level: -1 нема мережі, 0..4 сила. s — розмір значка, (cx,cy) — центр. */
    public static void wifi(Canvas c, Paint p, float cx, float cy, float s, int level, int color, boolean slash) {
        int lit = level < 0 ? 0 : level >= 3 ? 3 : level >= 2 ? 2 : 1;
        float ox = cx, oy = cy + s * 0.32f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.10f);
        for (int i = 1; i <= 3; i++) {
            float r = s * (0.04f + 0.20f * i);
            p.setColor(i <= lit ? color : fade(color, 0.32f));
            R.set(ox - r, oy - r, ox + r, oy + r);
            c.drawArc(R, 227, 86, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(level >= 0 ? color : fade(color, 0.32f));
        c.drawCircle(ox, oy, s * 0.075f, p);
        if (slash) slash(c, p, cx, cy, s);
    }

    public static void bluetooth(Canvas c, Paint p, float cx, float cy, float s, int color, boolean slash) {
        float top = cy - s * 0.46f, bot = cy + s * 0.46f, d = s * 0.22f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(s * 0.10f);
        p.setColor(color);
        Path path = new Path();
        path.moveTo(cx - d, cy - d * 0.92f);
        path.lineTo(cx + d, cy + d * 0.98f);
        path.lineTo(cx, bot);
        path.lineTo(cx, top);
        path.lineTo(cx + d, cy - d * 0.98f);
        path.lineTo(cx - d, cy + d * 0.92f);
        c.drawPath(path, p);
        if (slash) slash(c, p, cx, cy, s);
    }

    private static void slash(Canvas c, Paint p, float cx, float cy, float s) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.10f);
        p.setColor(0xFFFF453A);
        c.drawLine(cx - s * 0.46f, cy - s * 0.46f, cx + s * 0.46f, cy + s * 0.46f, p);
    }

    public static int fade(int color, float k) {
        int a = (int) ((color >>> 24) * k);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
