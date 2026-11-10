package ua.pc.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;

/** Значок Wi-Fi або Bluetooth для верхньої панелі. У фокусі — м'яке світле коло, без грубих рамок. */
public class StatusIcon extends View {
    public static final int WIFI = 0, BT = 1;
    private final int type;
    private int level = -1;
    private boolean btOn = true;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StatusIcon(Context c, int type) {
        super(c);
        this.type = type;
        setFocusable(true);
        setClickable(true);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    public void setWifiLevel(int lvl) { level = lvl; invalidate(); }
    public void setBtOn(boolean on) { btOn = on; invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) dp(46), (int) dp(46));
    }

    @Override protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        animate().scaleX(gain ? 1.12f : 1f).scaleY(gain ? 1.12f : 1f).setDuration(180).start();
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (isFocused()) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x4DFFFFFF);
            c.drawCircle(w / 2, h / 2, w / 2, p);
        }
        float s = dp(23);
        if (type == WIFI) Glyphs.wifi(c, p, w / 2, h / 2 + dp(1), s, level, 0xFFFFFFFF, false);
        else Glyphs.bluetooth(c, p, w / 2, h / 2, s * 0.95f, btOn ? 0xFFFFFFFF : 0x80FFFFFF, !btOn);
    }
}
