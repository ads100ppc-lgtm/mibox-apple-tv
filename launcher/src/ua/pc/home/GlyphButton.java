package ua.pc.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Велика кругла кнопка зі значком, як у Центрі керування iOS: синя, коли увімкнено; склянa, коли ні. */
public class GlyphButton extends View {
    public static final int ON = 0xFF0A84FF, OFF = 0x4DFFFFFF;
    private final int type;
    private boolean active;
    private float t;                // 0..1 — змішування кольору
    private int level = 3;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator anim;

    public GlyphButton(Context c, int type) {
        super(c);
        this.type = type;
        setFocusable(true);
        setClickable(true);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    public void setLevel(int l) { level = l; invalidate(); }
    public boolean isActive() { return active; }

    public void setActive(boolean on, boolean animate) {
        active = on;
        if (anim != null) anim.cancel();
        float to = on ? 1f : 0f;
        if (!animate) { t = to; invalidate(); return; }
        anim = ValueAnimator.ofFloat(t, to);
        anim.setDuration(280);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) dp(74), (int) dp(74));
    }

    @Override protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        animate().scaleX(gain ? 1.08f : 1f).scaleY(gain ? 1.08f : 1f).setDuration(200).start();
        invalidate();
    }

    @Override public void setPressed(boolean pr) {
        super.setPressed(pr);
        animate().scaleX(pr ? 0.94f : (isFocused() ? 1.08f : 1f)).scaleY(pr ? 0.94f : (isFocused() ? 1.08f : 1f)).setDuration(90).start();
    }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = dp(28);
        // тінь
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0x26000000);
        c.drawCircle(cx, cy + dp(3), r, p);
        // тіло
        p.setColor(blend(OFF, ON, t));
        c.drawCircle(cx, cy, r, p);
        // легкий відблиск зверху
        p.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
        // кільце фокусу — тонке, з проміжком
        if (isFocused()) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2f));
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, r + dp(4), p);
        }
        float s = dp(28);
        if (type == StatusIcon.WIFI) Glyphs.wifi(c, p, cx, cy + dp(1), s, active ? level : -1, 0xFFFFFFFF, false);
        else Glyphs.bluetooth(c, p, cx, cy, s * 0.95f, 0xFFFFFFFF, false);
    }

    private static int blend(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
