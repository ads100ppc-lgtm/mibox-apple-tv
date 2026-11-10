package ua.pc.keymap;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Запускає програми за натисканням двох кнопок пульта. Нічого іншого не робить. */
public class KeyService extends AccessibilityService {
    private static final String TAG = "KeyLauncher";

    // Android-коди кнопок і пакети програм (коди беремо із запису натискань).
    private static final int KEY_A = 192;
    private static final String PKG_A = "com.kyivstar.tv.androidtv";
    private static final int KEY_B = 194;
    private static final String PKG_B = "com.megogo.application";
    private static final int KEY_YT = 190;                       // кнопка YouTube
    private static final String PKG_YT = "com.google.android.youtube.tv";
    private static final int KEY_NF = 193;                       // кнопка Netflix
    private static final String PKG_NF = "com.netflix.ninja";
    private static final int KEY_APPS = 284;                     // кнопка Apps → «Усі програми» в лаунчері

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) Log.i(TAG, "key=" + code + " " + KeyEvent.keyCodeToString(code));
        String pkg = code == KEY_A ? PKG_A : code == KEY_B ? PKG_B : code == KEY_YT ? PKG_YT : code == KEY_NF ? PKG_NF : null;
        if (pkg == null && code != KEY_APPS) return false;
        // Поглинаємо і натискання, і відпускання, щоб система не відкрила видалені програми.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (code == KEY_APPS) openApps(); else launch(pkg);
        }
        return true;
    }

    private void openApps() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setPackage("ua.pc.home");
            i.putExtra("apps", true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Log.e(TAG, "apps failed", t);
        }
    }

    private void launch(String pkg) {
        try {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) { Log.w(TAG, "no launch intent for " + pkg); return; }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Throwable t) {
            Log.e(TAG, "launch failed", t);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }
}
