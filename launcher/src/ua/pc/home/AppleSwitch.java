package ua.pc.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Перемикач у стилі iOS: зелена доріжка, біла «кулька» з пружним ходом. */
public class AppleSwitch extends View {
    public interface Listener { void onChanged(boolean on); }

    private boolean on;
    private float pos;           // 0..1
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private Listener listener;
    private ValueAnimator anim;
    private float animScale = 1f;

    public AppleSwitch(Context c) {
        super(c);
        setFocusable(true);
        setClickable(true);
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { toggle(); }
        });
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    public void setAnimScale(float s) { animScale = s; }
    public void setListener(Listener l) { listener = l; }
    public boolean isOn() { return on; }

    public void setOn(boolean value, boolean animate) {
        if (on == value && !animate) return;
        on = value;
        if (anim != null) anim.cancel();
        float to = value ? 1f : 0f;
        if (!animate || animScale <= 0f) { pos = to; invalidate(); return; }
        anim = ValueAnimator.ofFloat(pos, to);
        anim.setDuration((long) (320 * animScale));
        anim.setInterpolator(new OvershootInterpolator(1.4f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { pos = (float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    public void toggle() {
        setOn(!on, true);
        if (listener != null) listener.onChanged(on);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension((int) dp(58), (int) dp(36));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pad = dp(3);
        // доріжка: сірувата → зелена
        int off = 0x59FFFFFF, onC = 0xFF34C759;
        float t = Math.max(0f, Math.min(1f, pos));
        p.setColor(blend(off, onC, t));
        r.set(pad, pad, w - pad, h - pad);
        c.drawRoundRect(r, h, h, p);
        // кулька
        float d = h - pad * 2 - dp(4);
        float x0 = pad + dp(2), x1 = w - pad - dp(2) - d;
        float x = x0 + (x1 - x0) * pos;
        p.setColor(0x33000000);
        c.drawCircle(x + d / 2, h / 2 + dp(1.5f), d / 2, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x + d / 2, h / 2, d / 2, p);
        if (isFocused()) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2.5f));
            p.setColor(0xFFFFFFFF);
            r.set(dp(1), dp(1), w - dp(1), h - dp(1));
            c.drawRoundRect(r, h, h, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    @Override protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        animate().scaleX(gain ? 1.12f : 1f).scaleY(gain ? 1.12f : 1f).setDuration((long) (160 * animScale)).start();
        invalidate();
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) { toggle(); return true; }
        return super.onKeyUp(keyCode, e);
    }

    private static int blend(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
