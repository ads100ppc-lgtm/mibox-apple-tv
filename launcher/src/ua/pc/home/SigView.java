package ua.pc.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

/** Мініатюра в рядку мережі: замок (якщо закрита) + значок Wi-Fi із силою сигналу. */
public class SigView extends View {
    private final int level;
    private final boolean secured;
    private int color = 0xFFFFFFFF;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SigView(Context c, int level, boolean secured) {
        super(c);
        this.level = level;
        this.secured = secured;
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    public void setColor(int c) { color = c; invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) dp(secured ? 46 : 26), (int) dp(26));
    }

    @Override protected void onDraw(Canvas c) {
        float h = getHeight(), w = getWidth();
        if (secured) {
            float s = dp(13), cx = dp(9), cy = h / 2f + dp(1);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.14f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);
            RectF sh = new RectF(cx - s * 0.28f, cy - s * 0.62f, cx + s * 0.28f, cy + s * 0.02f);
            c.drawArc(sh, 180, 180, false, p);
            c.drawLine(sh.left, cy - s * 0.30f, sh.left, cy, p);
            c.drawLine(sh.right, cy - s * 0.30f, sh.right, cy, p);
            p.setStyle(Paint.Style.FILL);
            RectF body = new RectF(cx - s * 0.46f, cy - s * 0.04f, cx + s * 0.46f, cy + s * 0.56f);
            c.drawRoundRect(body, s * 0.14f, s * 0.14f, p);
        }
        Glyphs.wifi(c, p, w - dp(13), h / 2f + dp(1), dp(18), level, color, false);
    }
}
