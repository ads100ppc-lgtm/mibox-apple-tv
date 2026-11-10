package ua.pc.home;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.List;

/**
 * Простий легкий головний екран у стилі Apple TV.
 * Налаштування: файл config.json у папці програми (див. loadConfig).
 */
public class HomeActivity extends Activity {

    // ---- налаштування за замовчуванням (перекриваються config.json) ----
    private static final String[] DEFAULT_APPS = {
            "com.google.android.youtube.tv", "top.rootu.lampa", "com.netflix.ninja",
            "com.kyivstar.tv.androidtv", "com.megogo.application",
            "com.android.vending", "com.macpaw.clearvpn.android", "com.android.tv.settings"
    };

    private float animScale = 1f;      // 0 = без анімацій, 1 = нормально
    private float zoom = 1.14f;        // наскільки збільшується плитка під фокусом
    private int cols = 5;              // плиток у рядку
    private int wallpaperMinutes = 5; // як часто міняти картинку фону
    private List<String> apps = new ArrayList<>();

    private FrameLayout root;
    private ImageView wallA, wallB;
    private ScrollView scroll;
    private LinearLayout rowsBox;
    private final Handler handler = new Handler();
    private List<File> wallpapers = new ArrayList<>();
    private int wallIndex = -1;
    private boolean wallFront = true;
    private DisplayMetrics dm;
    private final List<List<View>> rowsCards = new ArrayList<>();
    private boolean introPending = true;
    private String lastPkg = null;
    private float parallax = 1f;
    private boolean orbsOn = false;
    private float orbScale = 0.5f;
    private View clockBoxRef;
    private FrameLayout wallBox;
    private LinearLayout topBar, ambientBox;
    private TextView greetTv;
    private TextView weatherTv, ambientWeather;
    private StatusIcon wifiIcon, btIcon;
    private KB kenBurns;
    private boolean ambient = false;
    private FrameLayout panelLayer;
    private boolean panelOpen = false;
    private int ambientSeconds = 60;
    private Double cfgLat = null, cfgLon = null;
    private String cfgCity = null;
    private boolean btOffArmed = false;
    private boolean generating = false;
    private static final int R_PKG = 0x7f0b0001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dm = getResources().getDisplayMetrics();
        loadConfig();
        buildUi();
        hideSystemBars();
    }

    // ------------------------------------------------------------ config
    private File dir() {
        File d = new File("/sdcard/PcHome");
        if (!d.exists()) d.mkdirs();
        if (!d.exists() || !d.canWrite()) {
            d = getExternalFilesDir(null);
            if (d == null) d = getFilesDir();
        }
        return d;
    }

    private void loadConfig() {
        apps = new ArrayList<>(Arrays.asList(DEFAULT_APPS));
        File f = new File(dir(), "config.json");
        try {
            if (!f.exists()) {
                JSONObject o = new JSONObject();
                o.put("anim", 1.0);
                o.put("zoom", 1.14);
                o.put("cols", 5);
                o.put("wallpaperMinutes", 10);
                o.put("apps", new JSONArray(apps));
                FileOutputStream out = new FileOutputStream(f);
                out.write(o.toString(2).getBytes("UTF-8"));
                out.close();
            }
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = 0;
            while (n < buf.length) { int r = in.read(buf, n, buf.length - n); if (r < 0) break; n += r; }
            in.close();
            JSONObject o = new JSONObject(new String(buf, 0, n, "UTF-8"));
            animScale = (float) o.optDouble("anim", 1.0);
            zoom = (float) o.optDouble("zoom", 1.14);
            cols = Math.max(2, Math.min(8, o.optInt("cols", 5)));
            wallpaperMinutes = Math.max(1, o.optInt("wallpaperMinutes", 5));
            orbsOn = o.optBoolean("orbs", false);
            ambientSeconds = o.optInt("ambientSeconds", 60);
            cfgLat = o.has("lat") ? o.optDouble("lat") : null;
            cfgLon = o.has("lon") ? o.optDouble("lon") : null;
            cfgCity = o.has("city") ? o.optString("city") : null;
            orbScale = (float) o.optDouble("orbScale", 0.5);
            JSONArray a = o.optJSONArray("apps");
            if (a != null && a.length() > 0) {
                apps.clear();
                for (int i = 0; i < a.length(); i++) apps.add(a.getString(i));
            }
        } catch (Throwable t) {
            // залишаємо значення за замовчуванням
        }
    }

    // ------------------------------------------------------------ UI
    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm);
    }

    private static final long PROC_SEED = System.currentTimeMillis();

    private void buildUi() {
        Frost.setScreen(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);

        // фон: градієнт, поверх якого можна класти картинки
        View grad = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1B3A, 0xFF3A1E5C, 0xFF0E2A47});
        grad.setBackground(g);
        root.addView(grad, new FrameLayout.LayoutParams(-1, -1));

        // «кулі світла» що повільно пливуть (дешево: лише перетворення готових шарів)
        addOrb(0xFF7A5CFF, 1100, 0.55f, -0.25f, -0.2f, 38000);
        addOrb(0xFF1FB6FF, 900, 0.45f, 0.6f, 0.5f, 47000);
        addOrb(0xFFFF5CA8, 800, 0.35f, 0.9f, -0.1f, 53000);

        wallBox = new FrameLayout(this);
        wallA = new ImageView(this);
        wallA.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallA.setAlpha(0f);
        wallB = new ImageView(this);
        wallB.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallB.setAlpha(0f);
        wallBox.addView(wallA, new FrameLayout.LayoutParams(-1, -1));
        wallBox.addView(wallB, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wallBox, new FrameLayout.LayoutParams(-1, -1));

        // затемнення для читабельності
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x70000000, 0x14000000, 0x00000000, 0x99000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // верхня скляна панель: погода · Wi-Fi · Bluetooth · годинник
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        weatherTv = new TextView(this);
        weatherTv.setTextColor(Color.WHITE);
        weatherTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        weatherTv.setVisibility(View.GONE);
        topBar.addView(weatherTv);
        wifiIcon = new StatusIcon(this, StatusIcon.WIFI);
        btIcon = new StatusIcon(this, StatusIcon.BT);
        View.OnClickListener open = new View.OnClickListener() {
            @Override public void onClick(View v) { openQuickPanel(); }
        };
        wifiIcon.setOnClickListener(open);
        btIcon.setOnClickListener(open);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
        ip.leftMargin = dp(14);
        topBar.addView(wifiIcon, ip);
        LinearLayout.LayoutParams ip2 = new LinearLayout.LayoutParams(-2, -2);
        ip2.leftMargin = dp(4);
        View vdiv = new View(this);
        vdiv.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams vdp = new LinearLayout.LayoutParams(dp(1), dp(30));
        vdp.leftMargin = dp(14);
        topBar.addView(vdiv, vdp);
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.END);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        date.setGravity(Gravity.END);
        clockBox.addView(clock);
        clockBox.addView(date);
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-2, -2);
        cbp.leftMargin = dp(16);
        topBar.addView(clockBox, cbp);
        topBar.setBackground(new Frost(topBar, dp(30), 0x30000000));
        topBar.setPadding(dp(24), dp(6), dp(24), dp(8));
        topBar.setClipChildren(false);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cp.topMargin = dp(24);
        cp.rightMargin = dp(48);
        root.addView(topBar, cp);
        clockBoxRef = topBar;

        greetTv = new TextView(this);
        greetTv.setTextColor(Color.WHITE);
        greetTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        greetTv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        greetTv.setLetterSpacing(-0.01f);
        greetTv.setShadowLayer(18, 0, 4, 0x99000000);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        gp.leftMargin = dp(64);
        gp.topMargin = dp(40);
        root.addView(greetTv, gp);

        // плитки
        scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setGravity(Gravity.CENTER_VERTICAL);
        rowsBox.setClipChildren(false);
        rowsBox.setClipToPadding(false);
        int side = dp(56);
        rowsBox.setPadding(side, dp(150), side, dp(60));
        scroll.addView(rowsBox, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // режим заставки: великий годинник і погода
        ambientBox = new LinearLayout(this);
        ambientBox.setOrientation(LinearLayout.VERTICAL);
        TextClock bigClock = new TextClock(this);
        bigClock.setFormat24Hour("HH:mm");
        bigClock.setFormat12Hour("HH:mm");
        bigClock.setTextColor(Color.WHITE);
        bigClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 84);
        bigClock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        bigClock.setShadowLayer(14, 0, 3, 0x88000000);
        TextClock bigDate = new TextClock(this);
        bigDate.setFormat24Hour("EEEE, d MMMM");
        bigDate.setFormat12Hour("EEEE, d MMMM");
        bigDate.setTextColor(0xE6FFFFFF);
        bigDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        bigDate.setShadowLayer(10, 0, 2, 0x88000000);
        ambientWeather = new TextView(this);
        ambientWeather.setTextColor(0xE6FFFFFF);
        ambientWeather.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        ambientWeather.setShadowLayer(10, 0, 2, 0x88000000);
        ambientBox.addView(bigClock);
        ambientBox.addView(bigDate);
        ambientBox.addView(ambientWeather);
        ambientBox.setAlpha(0f);
        ambientBox.setVisibility(View.GONE);
        FrameLayout.LayoutParams abp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        abp.leftMargin = dp(64);
        abp.bottomMargin = dp(56);
        root.addView(ambientBox, abp);

        setContentView(root);
    }

    /** Велика м'яка кулька світла, що повільно дрейфує по екрану. */
    private void addOrb(int color, int sizeDp, float alpha, float fx, float fy, long period) {
        int size = dp(sizeDp * orbScale);
        GradientDrawable gd = new GradientDrawable();
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(size / 2f);
        gd.setColors(new int[]{(color & 0x00FFFFFF) | 0xAA000000, (color & 0x00FFFFFF) | 0x33000000, 0x00000000});
        gd.setGradientCenter(0.5f, 0.5f);
        View orb = new View(this);
        orb.setBackground(gd);
        orb.setAlpha(alpha);
        orb.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        orb.setLayoutParams(lp);
        orb.setTranslationX(dm.widthPixels * fx - size / 2f);
        orb.setTranslationY(dm.heightPixels * fy - size / 2f);
        orb.setTag(new float[]{fx, fy, period, orb.getTranslationX(), orb.getTranslationY(), alpha});
        root.addView(orb, 1 + orbCount++);
    }
    private int orbCount = 0;

    private long lastInteract = 0;
    private boolean ticking = false;
    private boolean orbsShown = true;

    /** Єдиний «пульс» лаунчера: рух кульок, відновлення повільного руху фону, перехід у заставку. */
    private final Runnable orbTick = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            boolean idle = now - lastInteract > 2500;
            if (idle) {
                if (orbsOn) {
                    if (!orbsShown) setOrbsShown(true);
                    for (int i = 1; i <= orbCount; i++) {
                        View o = root.getChildAt(i);
                        float[] t = (float[]) o.getTag();
                        double ph = 2 * Math.PI * (now % (long) t[2]) / t[2];
                        o.setTranslationX(t[3] + (float) Math.sin(ph) * dp(160));
                        o.setTranslationY(t[4] + (float) Math.cos(ph * 0.8) * dp(110));
                    }
                }
                if (kenBurns != null && kenBurns.isPaused()) kenBurns.resume();
            }
            if (!ambient && !panelOpen && ambientSeconds > 0 && now - lastInteract > ambientSeconds * 1000L) enterAmbient();
            if (ticking) handler.postDelayed(this, orbsOn ? 50 : 300);
        }
    };

    private void onInteract() {
        lastInteract = SystemClock.uptimeMillis();
        if (kenBurns != null && kenBurns.isRunning() && !kenBurns.isPaused()) kenBurns.pause();
        if (orbsShown && orbsOn) setOrbsShown(false);
    }

    private void setOrbsShown(boolean show) {
        orbsShown = show;
        for (int i = 1; i <= orbCount; i++) {
            View o = root.getChildAt(i);
            float[] t = (float[]) o.getTag();
            o.animate().cancel();
            o.animate().alpha(show ? t[5] : 0f).setDuration(show ? 1500 : 250).start();
        }
    }

    private void startOrbs() {
        stopOrbs();
        for (int i = 1; i <= orbCount; i++) root.getChildAt(i).setVisibility(orbsOn ? View.VISIBLE : View.GONE);
        ticking = true;
        handler.post(orbTick);
    }

    private void stopOrbs() {
        ticking = false;
        handler.removeCallbacks(orbTick);
    }

    // ------------------------------------------------------------ заставка
    private void enterAmbient() {
        ambient = true;
        long d = (long) (900 * Math.max(animScale, 0.2f));
        scroll.animate().alpha(0f).setDuration(d).start();
        topBar.animate().alpha(0f).setDuration(d).start();
        greetTv.animate().alpha(0f).setDuration(d).start();
        ambientBox.setVisibility(View.VISIBLE);
        ambientBox.setTranslationY(dp(20));
        ambientBox.animate().alpha(1f).translationY(0f).setDuration(d + 300).setInterpolator(new DecelerateInterpolator()).start();
        if (kenBurns != null && kenBurns.isPaused()) kenBurns.resume();
    }

    private void exitAmbient() {
        ambient = false;
        long d = (long) (400 * Math.max(animScale, 0.2f));
        scroll.animate().alpha(1f).setDuration(d).start();
        topBar.animate().alpha(1f).setDuration(d).start();
        greetTv.animate().alpha(1f).setDuration(d).start();
        ambientBox.animate().alpha(0f).setDuration(d).withEndAction(new Runnable() {
            @Override public void run() { ambientBox.setVisibility(View.GONE); }
        }).start();
        View f = lastPkg != null ? findCard(lastPkg) : null;
        if (f == null) f = rowsBox.findViewWithTag("card");
        if (f != null) f.requestFocus();
    }

    private View findCard(String pkg) {
        for (List<View> row : rowsCards) for (View c : row) if (pkg.equals(c.getTag(R_PKG))) return c;
        return null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) onInteract();
        if (ambient) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) exitAmbient();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ------------------------------------------------------------ повільний рух фону
    /** Дуже повільний рух фону. Оновлюється ~8 разів на секунду (а не 60): різниці на око немає, а екран не перемальовується постійно. */
    private class KB {
        private boolean running, paused;
        private long phaseMs, last;
        private final long half;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                long now = SystemClock.uptimeMillis();
                if (!paused) phaseMs += now - last;
                last = now;
                float f = (phaseMs % (2 * half)) / (float) half;
                if (f > 1f) f = 2f - f;
                wallBox.setScaleX(1.06f + 0.10f * f);
                wallBox.setScaleY(1.06f + 0.10f * f);
                wallBox.setTranslationX(dp(18) - dp(36) * f);
                if (running && !paused) handler.postDelayed(this, 125);
            }
        };
        KB(long halfMs) { half = halfMs; }
        void start() { running = true; paused = false; last = SystemClock.uptimeMillis(); handler.post(tick); }
        void pause() { paused = true; handler.removeCallbacks(tick); }
        void resume() { if (!running) return; paused = false; last = SystemClock.uptimeMillis(); handler.post(tick); }
        void cancel() { running = false; handler.removeCallbacks(tick); }
        boolean isPaused() { return paused; }
        boolean isRunning() { return running; }
    }

    private void startKenBurns() {
        if (kenBurns != null) kenBurns.cancel();
        wallBox.setScaleX(1.06f);
        wallBox.setScaleY(1.06f);
        wallBox.setTranslationX(dp(18));
        if (animScale <= 0f) return;
        kenBurns = new KB((long) (70000 / animScale));
        kenBurns.start();
    }

    private void stopKenBurns() {
        if (kenBurns != null) { kenBurns.cancel(); kenBurns = null; }
    }

    // ------------------------------------------------------------ генерація фонів
    private void generateWallpapersIfNeeded() {
        if (generating || !wallpapers.isEmpty()) return;
        generating = true;
        final File d = new File(dir(), "wallpapers");
        d.mkdirs();
        new Thread(new Runnable() {
            @Override public void run() {
                for (String name : SceneArt.NAMES) {
                    try {
                        Bitmap b = SceneArt.render(name, 1280, 720);
                        FileOutputStream out = new FileOutputStream(new File(d, "gen_" + name + ".jpg"));
                        b.compress(Bitmap.CompressFormat.JPEG, 88, out);
                        out.close();
                        b.recycle();
                    } catch (Throwable ignored) { }
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        generating = false;
                        scanWallpapers();
                        handler.removeCallbacks(nextWall);
                        if (!wallpapers.isEmpty()) handler.post(nextWall);
                    }
                });
            }
        }, "scenes").start();
    }

    // ------------------------------------------------------------ статус Wi-Fi / Bluetooth
    private final BroadcastReceiver statusRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateStatus(); if (panelOpen) refreshWifiList(); }
    };

    /** -1 = не підключено; 0..4 = сила сигналу (якщо Android ховає RSSI — вважаємо «добрий»). */
    private int wifiLevel(WifiManager wm) {
        boolean connected = false;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.Network n = cm.getActiveNetwork();
            android.net.NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            connected = nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Throwable ignored) { }
        if (!connected) return -1;
        try {
            int rssi = wm.getConnectionInfo().getRssi();
            if (rssi > -127 && rssi < 0) return WifiManager.calculateSignalLevel(rssi, 5);
        } catch (Throwable ignored) { }
        return 3;
    }

    private void updateStatus() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int lvl = -1;
            if (wm != null && wm.isWifiEnabled()) {
                lvl = wifiLevel(wm);
            }
            wifiIcon.setWifiLevel(lvl);
        } catch (Throwable ignored) { }
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            btIcon.setBtOn(ba != null && ba.isEnabled());
        } catch (Throwable ignored) { }
    }

    private void registerStatus() {
        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        try { registerReceiver(statusRx, f); } catch (Throwable ignored) { }
        updateStatus();
    }

    private void unregisterStatus() {
        try { unregisterReceiver(statusRx); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------ погода
    private final Weather.Listener weatherUi = new Weather.Listener() {
        @Override public void onWeather(final String emoji, final String temp, final String city) {
            runOnUiThread(new Runnable() {
                @Override public void run() { showWeather(emoji, temp, city); }
            });
        }
    };

    private void showWeather(String emoji, String temp, String city) {
        String t = emoji + " " + temp;
        weatherTv.setText(t);
        weatherTv.setVisibility(View.VISIBLE);
        ambientWeather.setText(t + (city == null || city.isEmpty() ? "" : "  ·  " + city));
    }

    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            Weather.fetch(getApplicationContext(), cfgLat, cfgLon, cfgCity, weatherUi);
            handler.postDelayed(this, 30 * 60_000L);
        }
    };

    private void startWeather() {
        Weather.cached(this, new Weather.Listener() {
            @Override public void onWeather(String e, String t, String c) { showWeather(e, t, c); }
        });
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
    }

    // ------------------------------------------------------------ меню швидких налаштувань
    private TextView pill(String text, final Runnable action) {
        final TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(10), dp(11), dp(10), dp(11));
        t.setFocusable(true);
        t.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(0x33FFFFFF);
        t.setBackground(bg);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xF2FFFFFF : 0x33FFFFFF);
                t.setTextColor(has ? 0xFF111111 : Color.WHITE);
                v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(160).start();
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return t;
    }

    private LinearLayout row(String title, String sub, View right) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView a = new TextView(this);
        a.setText(title);
        a.setTextColor(Color.WHITE);
        a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        TextView b = new TextView(this);
        b.setText(sub);
        b.setTextColor(0xB3FFFFFF);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        txt.addView(a);
        txt.addView(b);
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(right, new LinearLayout.LayoutParams(-2, -2));
        r.setPadding(0, dp(10), dp(8), dp(10));
        r.setClipToPadding(false);
        r.setClipChildren(false);
        return r;
    }

    private void openActivity(String action) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Не вдалося відкрити", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView mkText(String t, float sp, int color, String face) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        if (face != null) v.setTypeface(Typeface.create(face, Typeface.NORMAL));
        return v;
    }

    // ------------------------------------------------------------ Wi-Fi: список мереж
    private LinearLayout wifiList;
    private TextView wifiSub;
    private boolean pwMode = false;
    private boolean needFocus = false;
    private String wifiSig = "";
    private String connectingTo = null;

    private final Runnable scanTick = new Runnable() {
        @Override public void run() {
            if (!panelOpen) return;
            try { ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).startScan(); } catch (Throwable ignored) { }
            handler.postDelayed(this, 12000);
        }
    };

    private static String unq(String s) {
        if (s == null) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    private boolean isSecured(String caps) {
        return caps != null && (caps.contains("WPA") || caps.contains("WEP") || caps.contains("SAE") || caps.contains("PSK"));
    }

    private String currentSsid(WifiManager wm) {
        try {
            int rssi = wm.getConnectionInfo().getRssi();
            String ssid = unq(wm.getConnectionInfo().getSSID());
            if (ssid.isEmpty() || "<unknown ssid>".equals(ssid) || wm.getConnectionInfo().getNetworkId() == -1) return null;
            return ssid;
        } catch (Throwable t) { return null; }
    }

    private void refreshWifiList() {
        if (!panelOpen || wifiList == null || pwMode) return;
        final WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean on = wm.isWifiEnabled();
        String cur = on ? currentSsid(wm) : null;
        java.util.LinkedHashMap<String, android.net.wifi.ScanResult> best = new java.util.LinkedHashMap<>();
        if (on) {
            try {
                List<android.net.wifi.ScanResult> rs = wm.getScanResults();
                if (rs != null) for (android.net.wifi.ScanResult r : rs) {
                    if (r.SSID == null || r.SSID.isEmpty()) continue;
                    android.net.wifi.ScanResult o = best.get(r.SSID);
                    if (o == null || r.level > o.level) best.put(r.SSID, r);
                }
            } catch (Throwable ignored) { }
        }
        List<android.net.wifi.ScanResult> sorted = new ArrayList<>(best.values());
        Collections.sort(sorted, new java.util.Comparator<android.net.wifi.ScanResult>() {
            @Override public int compare(android.net.wifi.ScanResult a, android.net.wifi.ScanResult b) { return b.level - a.level; }
        });
        Set<String> saved = new java.util.HashSet<>();
        try {
            List<android.net.wifi.WifiConfiguration> cs = wm.getConfiguredNetworks();
            if (cs != null) for (android.net.wifi.WifiConfiguration c : cs) saved.add(unq(c.SSID));
        } catch (Throwable ignored) { }

        StringBuilder sig = new StringBuilder(on ? "1" : "0").append('|').append(cur).append('|').append(connectingTo);
        for (android.net.wifi.ScanResult r : sorted) sig.append('|').append(r.SSID).append(WifiManager.calculateSignalLevel(r.level, 5));
        if (sig.toString().equals(wifiSig)) return;
        wifiSig = sig.toString();

        // що було у фокусі — повернемо після перебудови
        String focusedSsid = null;
        View fv = wifiList.getFocusedChild();
        if (fv != null && fv.getTag() instanceof String) focusedSsid = (String) fv.getTag();
        boolean hadFocus = fv != null;

        wifiList.removeAllViews();
        View toFocus = null;
        if (!on) {
            wifiList.addView(mkText("Wi-Fi вимкнено", 16, 0xB3FFFFFF, null), new LinearLayout.LayoutParams(-1, dp(80)));
        } else if (sorted.isEmpty() && cur == null) {
            wifiList.addView(mkText("Шукаю мережі…", 16, 0xB3FFFFFF, null), new LinearLayout.LayoutParams(-1, dp(80)));
        } else {
            if (cur != null) {
                android.net.wifi.ScanResult r = best.get(cur);
                int lv = r != null ? WifiManager.calculateSignalLevel(r.level, 5) : wifiLevel(wm);
                View row = wifiRow(cur, lv, r != null && isSecured(r.capabilities), true, true, r);
                wifiList.addView(row);
                toFocus = row;
            }
            boolean header = false;
            for (android.net.wifi.ScanResult r : sorted) {
                if (r.SSID.equals(cur)) continue;
                if (!header) {
                    header = true;
                    TextView h = mkText("Доступні мережі", 12, 0x99FFFFFF, "sans-serif-medium");
                    h.setGravity(Gravity.START);
                    h.setPadding(dp(10), dp(10), 0, dp(2));
                    wifiList.addView(h);
                }
                View row = wifiRow(r.SSID, WifiManager.calculateSignalLevel(r.level, 5), isSecured(r.capabilities), false, saved.contains(r.SSID), r);
                wifiList.addView(row);
                if (toFocus == null) toFocus = row;
            }
        }
        // підпис під заголовком
        if (!on) wifiSub.setText("Вимкнено");
        else if (connectingTo != null) wifiSub.setText("Підключення до «" + connectingTo + "»…");
        else wifiSub.setText(cur != null ? "Підключено до «" + cur + "»" : "Не підключено");
        if (hadFocus || needFocus || wifiList.getRootView().findFocus() == null) {
            needFocus = false;
            View target = null;
            if (focusedSsid != null) target = wifiList.findViewWithTag(focusedSsid);
            if (target == null) target = toFocus;
            if (target != null) target.requestFocus();
        }
    }

    private View wifiRow(final String ssid, int level, boolean secured, boolean connected, final boolean saved, final android.net.wifi.ScanResult sr) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setFocusable(true);
        row.setClickable(true);
        row.setTag(ssid);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0x00000000);
        row.setBackground(bg);
        final TextView check = mkText(connected ? "\u2713" : "", 17, 0xFF0A84FF, "sans-serif-medium");
        row.addView(check, new LinearLayout.LayoutParams(dp(24), -2));
        final TextView name = mkText(ssid, 16, Color.WHITE, connected ? "sans-serif-medium" : null);
        name.setGravity(Gravity.START);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        final SigView sv = new SigView(this, level, secured);
        row.addView(sv);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xF2FFFFFF : 0x00000000);
                name.setTextColor(has ? 0xFF111111 : Color.WHITE);
                sv.setColor(has ? 0xFF111111 : 0xFFFFFFFF);
            }
        });
        final boolean isConn = connected;
        final boolean sec = secured;
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isConn) return;
                connectTo(ssid, sr, saved, sec);
            }
        });
        return row;
    }

    private void connectTo(String ssid, android.net.wifi.ScanResult sr, boolean saved, boolean secured) {
        final WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            if (saved) {
                for (android.net.wifi.WifiConfiguration c : wm.getConfiguredNetworks()) {
                    if (unq(c.SSID).equals(ssid)) { doConnect(wm, c.networkId, ssid); return; }
                }
            }
            if (!secured) {
                android.net.wifi.WifiConfiguration c = new android.net.wifi.WifiConfiguration();
                c.SSID = "\"" + ssid + "\"";
                c.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE);
                int id = wm.addNetwork(c);
                if (id >= 0) { doConnect(wm, id, ssid); return; }
                Toast.makeText(this, "Не вдалося додати мережу", Toast.LENGTH_SHORT).show();
                return;
            }
            showPasswordForm(ssid, sr);
        } catch (Throwable t) {
            Toast.makeText(this, "Не вдалося підключитися", Toast.LENGTH_SHORT).show();
        }
    }

    private void doConnect(WifiManager wm, int id, String ssid) {
        connectingTo = ssid;
        wifiSig = "";
        wm.disconnect();
        wm.enableNetwork(id, true);
        wm.reconnect();
        refreshWifiList();
        handler.postDelayed(new Runnable() {
            @Override public void run() { connectingTo = null; wifiSig = ""; refreshWifiList(); }
        }, 12000);
    }

    private void showPasswordForm(final String ssid, final android.net.wifi.ScanResult sr) {
        pwMode = true;
        wifiList.removeAllViews();
        TextView t = mkText("Пароль для «" + ssid + "»", 17, Color.WHITE, "sans-serif-medium");
        t.setGravity(Gravity.START);
        t.setPadding(dp(6), dp(8), 0, dp(10));
        wifiList.addView(t);
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setSingleLine(true);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0x80FFFFFF);
        et.setHint("Введіть пароль Wi-Fi");
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        et.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable ebg = new GradientDrawable();
        ebg.setCornerRadius(dp(16));
        ebg.setColor(0x33FFFFFF);
        ebg.setStroke(dp(1), 0x55FFFFFF);
        et.setBackground(ebg);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        wifiList.addView(et, new LinearLayout.LayoutParams(-1, -2));
        final Runnable go = new Runnable() {
            @Override public void run() {
                String pw = et.getText().toString();
                if (pw.length() < 8) { Toast.makeText(HomeActivity.this, "Пароль має бути не коротший за 8 символів", Toast.LENGTH_SHORT).show(); return; }
                try {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    android.net.wifi.WifiConfiguration c = new android.net.wifi.WifiConfiguration();
                    c.SSID = "\"" + ssid + "\"";
                    String caps = sr == null ? "" : sr.capabilities;
                    c.preSharedKey = "\"" + pw + "\"";
                    if (caps.contains("SAE") && !caps.contains("PSK")) c.allowedKeyManagement.set(8);
                    else c.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK);
                    int id = wm.addNetwork(c);
                    pwMode = false;
                    hideKeyboard(et);
                    if (id >= 0) { wm.saveConfiguration(); doConnect(wm, id, ssid); }
                    else { wifiSig = ""; refreshWifiList(); Toast.makeText(HomeActivity.this, "Не вдалося додати мережу", Toast.LENGTH_SHORT).show(); }
                } catch (Throwable ex) {
                    pwMode = false; wifiSig = ""; refreshWifiList();
                }
            }
        };
        et.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(android.widget.TextView v, int actionId, KeyEvent e) { go.run(); return true; }
        });
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(14), 0, 0);
        TextView ok = pill("Підключити", go);
        TextView cancel = pill("Скасувати", new Runnable() {
            @Override public void run() { hideKeyboard(et); pwMode = false; needFocus = true; wifiSig = ""; refreshWifiList(); }
        });
        btns.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(0, -2, 1f);
        okp.leftMargin = dp(10);
        btns.addView(ok, okp);
        wifiList.addView(btns);
        et.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(et, 0);
    }

    private void hideKeyboard(View v) {
        try {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Throwable ignored) { }
    }

    private void openQuickPanel() {
        if (panelOpen) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 7);
        }
        panelOpen = true;
        pwMode = false;
        needFocus = true;
        wifiSig = "";
        connectingTo = null;
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        topBar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        panelLayer = new FrameLayout(this);
        panelLayer.setBackgroundColor(0x44000000);
        panelLayer.setAlpha(0f);

        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(new Frost(card, dp(30), 0x70101428));
        card.setPadding(dp(20), dp(18), dp(20), dp(16));
        card.setClipToPadding(false);
        card.setClipChildren(false);

        final WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // заголовок: «Wi-Fi» + підпис + перемикач
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), 0, dp(6), 0);
        head.setClipChildren(false);
        head.setClipToPadding(false);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView name = mkText("Wi-Fi", 20, Color.WHITE, "sans-serif-medium");
        name.setGravity(Gravity.START);
        wifiSub = mkText("", 13, 0xB3FFFFFF, null);
        wifiSub.setGravity(Gravity.START);
        wifiSub.setSingleLine(true);
        wifiSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.addView(name);
        txt.addView(wifiSub);
        head.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        final AppleSwitch sw = new AppleSwitch(this);
        sw.setAnimScale(animScale);
        boolean wifiOn = false;
        try { wifiOn = wm.isWifiEnabled(); } catch (Throwable ignored) { }
        sw.setOn(wifiOn, false);
        sw.setListener(new AppleSwitch.Listener() {
            @Override public void onChanged(boolean on) {
                try { wm.setWifiEnabled(on); } catch (Throwable ignored) { }
                wifiSig = "";
                handler.postDelayed(new Runnable() { @Override public void run() { refreshWifiList(); } }, 1200);
                handler.postDelayed(new Runnable() { @Override public void run() { refreshWifiList(); } }, 3500);
            }
        });
        head.addView(sw);
        card.addView(head);

        View hair = new View(this);
        hair.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(1));
        hp.topMargin = dp(10);
        hp.bottomMargin = dp(6);
        card.addView(hair, hp);

        // список мереж (з прокруткою, не вище ~5 рядків)
        ScrollView ws = new ScrollView(this) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(dp(250), MeasureSpec.AT_MOST));
            }
        };
        ws.setVerticalScrollBarEnabled(false);
        ws.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wifiList = new LinearLayout(this);
        wifiList.setOrientation(LinearLayout.VERTICAL);
        ws.addView(wifiList, new ViewGroup.LayoutParams(-1, -2));
        card.addView(ws, new LinearLayout.LayoutParams(-1, -2));

        View hair2 = new View(this);
        hair2.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams hp2 = new LinearLayout.LayoutParams(-1, dp(1));
        hp2.topMargin = dp(6);
        hp2.bottomMargin = dp(12);
        card.addView(hair2, hp2);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setClipChildren(false);
        TextView rescan = pill("Оновити", new Runnable() {
            @Override public void run() {
                try { wm.startScan(); } catch (Throwable ignored) { }
                wifiSig = "";
                refreshWifiList();
            }
        });
        TextView all = pill("Налаштування", new Runnable() {
            @Override public void run() { closeQuickPanel(); openActivity(Settings.ACTION_SETTINGS); }
        });
        rescan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        all.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btns.addView(rescan, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1f);
        ap.leftMargin = dp(10);
        btns.addView(all, ap);
        card.addView(btns, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(400), -2, Gravity.TOP | Gravity.END);
        cp.topMargin = dp(108);
        cp.rightMargin = dp(48);
        panelLayer.addView(card, cp);
        root.addView(panelLayer, new FrameLayout.LayoutParams(-1, -1));

        card.setPivotX(dp(400));
        card.setPivotY(0);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.setAlpha(0f);
        long d = (long) (380 * animScale);
        panelLayer.animate().alpha(1f).setDuration(d).start();
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(d).setInterpolator(new OvershootInterpolator(0.9f)).start();

        refreshWifiList();
        handler.removeCallbacks(scanTick);
        handler.post(scanTick);
    }

    // ------------------------------------------------------------ «Усі програми»
    private FrameLayout appsLayer;
    private boolean appsOpen = false;

    private void openAllApps() {
        if (appsOpen) return;
        if (panelOpen) closeQuickPanel();
        if (ambient) exitAmbient();
        appsOpen = true;
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        topBar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        appsLayer = new FrameLayout(this);
        appsLayer.setBackgroundColor(0x55000000);
        appsLayer.setAlpha(0f);

        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(new Frost(card, dp(34), 0x80101428));
        card.setPadding(dp(28), dp(22), dp(28), dp(18));
        card.setClipToPadding(false);
        card.setClipChildren(false);
        TextView title = mkText("Усі програми", 22, Color.WHITE, "sans-serif-medium");
        title.setGravity(Gravity.START);
        title.setPadding(dp(8), 0, 0, dp(10));
        card.addView(title);

        PackageManager pm = getPackageManager();
        final List<ResolveInfo> apps = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pk = ri.activityInfo.packageName;
                if (pk.equals(getPackageName()) || !seen.add(pk)) continue;
                apps.add(ri);
            }
        }
        final PackageManager fpm = pm;
        Collections.sort(apps, new java.util.Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(fpm).toString().compareToIgnoreCase(b.loadLabel(fpm).toString());
            }
        });

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        final int cols = 6;
        View first = null;
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            View cell = appCell(pm, apps.get(i));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.setMargins(dp(5), dp(3), dp(5), dp(3));
            row.addView(cell, clp);
            if (first == null) first = cell;
        }
        if (row != null) for (int k = row.getChildCount(); k < cols; k++) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        sv.addView(rows, new ViewGroup.LayoutParams(-1, -2));
        card.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -1);
        cp.setMargins(dp(110), dp(70), dp(110), dp(60));
        appsLayer.addView(card, cp);
        root.addView(appsLayer, new FrameLayout.LayoutParams(-1, -1));

        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.setAlpha(0f);
        long d = (long) (380 * animScale);
        appsLayer.animate().alpha(1f).setDuration(d).start();
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(d).setInterpolator(new DecelerateInterpolator()).start();
        if (first != null) first.requestFocus();
    }

    private View appCell(final PackageManager pm, final ResolveInfo ri) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(4), dp(10), dp(4), dp(8));
        c.setFocusable(true);
        c.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0x00000000);
        c.setBackground(bg);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(ri.loadIcon(pm));
        ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        c.addView(ic, new LinearLayout.LayoutParams(dp(64), dp(64)));
        final TextView lb = mkText(ri.loadLabel(pm).toString(), 14, Color.WHITE, null);
        lb.setSingleLine(true);
        lb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        c.addView(lb, lp);
        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xF2FFFFFF : 0x00000000);
                lb.setTextColor(has ? 0xFF111111 : Color.WHITE);
            }
        });
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent li = launchIntent(pm, ri.activityInfo.packageName);
                if (li == null) return;
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                closeAllApps();
                try { startActivity(li); } catch (Throwable ignored) { }
            }
        });
        return c;
    }

    private void closeAllApps() {
        if (!appsOpen) return;
        appsOpen = false;
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        topBar.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        final View layer = appsLayer;
        appsLayer = null;
        layer.animate().alpha(0f).setDuration((long) (200 * animScale)).withEndAction(new Runnable() {
            @Override public void run() { root.removeView(layer); }
        }).start();
        View f = lastPkg != null ? findCard(lastPkg) : null;
        if (f == null) f = rowsBox.findViewWithTag("card");
        if (f != null) f.requestFocus();
    }

    private void closeQuickPanel() {
        if (!panelOpen) return;
        panelOpen = false;
        pwMode = false;
        handler.removeCallbacks(scanTick);
        wifiList = null;
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        topBar.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        final View layer = panelLayer;
        panelLayer = null;
        layer.animate().alpha(0f).setDuration((long) (200 * animScale)).withEndAction(new Runnable() {
            @Override public void run() { root.removeView(layer); }
        }).start();
        View f = lastPkg != null ? findCard(lastPkg) : null;
        if (f == null) f = rowsBox.findViewWithTag("card");
        if (f != null) f.requestFocus();
        updateStatus();
    }

    private void fillTiles() {
        rowsBox.removeAllViews();
        rowsCards.clear();
        PackageManager pm = getPackageManager();
        int side = dp(56);
        int gap = dp(24);
        int tileW = (dm.widthPixels - 2 * side - (cols - 1) * gap) / cols;
        int tileH = tileW * 9 / 16 + dp(2);

        LinearLayout row = null;
        int inRow = 0;
        View first = null;
        View restore = null;
        for (String pkg : apps) {
            Intent li = launchIntent(pm, pkg);
            if (li == null) continue;
            if (row == null || inRow == cols) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = dp(40);
                rowsBox.addView(row, rp);
                rowsCards.add(new ArrayList<View>());
                inRow = 0;
            }
            View tile = makeTile(pm, pkg, li, tileW, tileH);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
            if (inRow > 0) lp.leftMargin = gap;
            row.addView(tile, lp);
            View cardV = tile.findViewWithTag("card");
            rowsCards.get(rowsCards.size() - 1).add(cardV);
            cardV.setTag(R_PKG, pkg);
            if (first == null) first = cardV;
            if (pkg.equals(lastPkg)) restore = cardV;
            inRow++;
        }
        View target = restore != null ? restore : first;
        if (target != null) target.requestFocus();
        if (introPending) { playIntro(); introPending = false; }
    }

    private Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    private View makeTile(PackageManager pm, final String pkg, final Intent li, int w, int h) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);

        final FrameLayout card = new FrameLayout(this);
        card.setTag("card");
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipChildren(true);
        card.setBackgroundColor(0xFF22262F);
        final float radius = dp(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);

        Drawable banner = null;
        String label = pkg;
        try {
            label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            ResolveInfo ri = pm.resolveActivity(li, 0);
            if (ri != null) {
                banner = ri.activityInfo.loadBanner(pm);
                if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            }
        } catch (Throwable ignored) { }

        ImageView img = new ImageView(this);
        if (banner != null) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(banner);
            card.addView(img, new FrameLayout.LayoutParams(-1, h));
        } else {
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(pkg); } catch (Throwable ignored) { }
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setImageDrawable(icon);
            int pad = h / 5;
            img.setPadding(pad, pad, pad, pad);
            card.addView(img, new FrameLayout.LayoutParams(-1, h));
        }
        // тонка рамка при фокусі
        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(radius);
        ring.setStroke(dp(3), 0xFFFFFFFF);
        ring.setColor(Color.TRANSPARENT);
        final View ringView = new View(this);
        ringView.setBackground(ring);
        ringView.setAlpha(0f);
        card.addView(ringView, new FrameLayout.LayoutParams(-1, h));

        wrap.addView(card, new LinearLayout.LayoutParams(w, h));

        final TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setSingleLine(true);
        name.setAlpha(0f);
        name.setShadowLayer(6, 0, 2, 0xAA000000);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(12);
        wrap.addView(name, np);

        // блиск: похила світла смуга, що пробігає по плитці при фокусі
        final View shine = new View(this);
        GradientDrawable sh = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF});
        shine.setBackground(sh);
        shine.setRotation(18f);
        shine.setAlpha(0f);
        card.addView(shine, new FrameLayout.LayoutParams((int) (w * 0.38f), (int) (h * 2.2f)));
        shine.setTranslationY(-h * 0.6f);
        shine.setTranslationX(-w);
        final int cardW = w;
        card.setCameraDistance(dm.density * 9000f);
        card.setElevation(dp(6));

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                lastPkgIfFocused(v, has);
                lastInteract = SystemClock.uptimeMillis();
                if (orbsShown && orbsOn) setOrbsShown(false);
                float s = has ? zoom : 1f;
                long dur = (long) (260 * animScale);
                v.animate().cancel();
                // нахил залежно від положення на екрані (ліві плитки дивляться вправо, праві вліво)
                float cx = v.getLeft() + ((View) v.getParent()).getLeft() + cardW / 2f;
                float tilt = ((cx / dm.widthPixels) - 0.5f) * -14f;
                if (dur <= 0) {
                    v.setScaleX(s); v.setScaleY(s); v.setTranslationZ(has ? dp(18) : 0);
                    v.setRotationY(has ? tilt : 0f);
                    ringView.setAlpha(has ? 1f : 0f); name.setAlpha(has ? 1f : 0f);
                } else {
                    v.animate().scaleX(s).scaleY(s).translationZ(has ? dp(18) : 0)
                            .rotationY(has ? tilt : 0f)
                            .setDuration(dur).setInterpolator(new OvershootInterpolator(has ? 1.6f : 0f)).start();
                    ringView.animate().alpha(has ? 1f : 0f).setDuration(dur).start();
                    name.animate().alpha(has ? 1f : 0f).translationY(has ? 0f : -dp(6)).setDuration(dur).start();
                    if (has) {
                        shine.animate().cancel();
                        shine.setAlpha(1f);
                        shine.setTranslationX(-cardW * 0.5f);
                        shine.animate().translationX(cardW * 1.2f).setDuration((long) (700 * animScale))
                                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
                    }
                }
                if (has) { applyWave(v); parallaxTo(v); }
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launch(pkg, v); }
        });
        card.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_UP &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    launch(pkg, v);
                    return true;
                }
                return false;
            }
        });
        return wrap;
    }

    private void launch(String pkg, View from) {
        try {
            PackageManager pm = getPackageManager();
            Intent i = launchIntent(pm, pkg);
            if (i == null && "com.android.tv.settings".equals(pkg)) i = new Intent(Settings.ACTION_SETTINGS);
            if (i == null) { Toast.makeText(this, "Програму не знайдено", Toast.LENGTH_SHORT).show(); return; }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (from != null && animScale > 0f) {
                ActivityOptions o = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight());
                startActivity(i, o.toBundle());
            } else {
                startActivity(i);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Не вдалося відкрити", Toast.LENGTH_SHORT).show();
        }
    }

    private void lastPkgIfFocused(View v, boolean has) {
        if (has) { Object t = v.getTag(R_PKG); if (t != null) lastPkg = (String) t; }
    }

    /** «Магніт»: сусідні плитки в рядку розсуваються від тієї, що під фокусом. */
    private void applyWave(View focused) {
        if (animScale <= 0f) return;
        for (List<View> row : rowsCards) {
            int fi = row.indexOf(focused);
            for (int i = 0; i < row.size(); i++) {
                View c = row.get(i);
                float push = 0f;
                if (fi >= 0 && i != fi) {
                    int d = i - fi;
                    float falloff = 1f / (Math.abs(d));
                    push = Math.signum(d) * c.getWidth() * (zoom - 1f) * 0.55f * falloff;
                }
                if (fi < 0) {
                    // інші рядки повертаються на місце
                    push = 0f;
                }
                if (i != fi) c.animate().translationX(push).setDuration((long) (320 * animScale))
                        .setInterpolator(new DecelerateInterpolator()).start();
                else c.animate().translationX(0f).setDuration((long) (320 * animScale)).start();
            }
        }
    }

    /** Паралакс: фон ледь зсувається проти руху фокусу. */
    private void parallaxTo(View focused) {
        if (animScale <= 0f) return;
        float cx = focused.getLeft() + ((View) focused.getParent()).getLeft() + focused.getWidth() / 2f;
        float cy = ((View) focused.getParent().getParent()).getTop() + focused.getTop();
        float dx = -((cx / dm.widthPixels) - 0.5f) * dp(46);
        float dy = -((cy / dm.heightPixels) - 0.5f) * dp(26);
        wallA.animate().translationX(dx).translationY(dy).scaleX(1.06f).scaleY(1.06f)
                .setDuration((long) (700 * animScale)).setInterpolator(new DecelerateInterpolator()).start();
        wallB.animate().translationX(dx).translationY(dy).scaleX(1.06f).scaleY(1.06f)
                .setDuration((long) (700 * animScale)).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Вхід: плитки вилітають знизу каскадом з пружним відскоком. */
    private void playIntro() {
        if (animScale <= 0f) return;
        int idx = 0;
        for (List<View> row : rowsCards) {
            for (View c : row) {
                View wrap = (View) c.getParent();
                wrap.setAlpha(0f);
                wrap.setTranslationY(dp(90));
                wrap.setRotationX(18f);
                wrap.animate().alpha(1f).translationY(0f).rotationX(0f)
                        .setStartDelay((long) (idx * 70 * animScale))
                        .setDuration((long) (650 * animScale))
                        .setInterpolator(new OvershootInterpolator(1.2f)).start();
                idx++;
            }
        }
        if (clockBoxRef != null) {
            clockBoxRef.setAlpha(0f);
            clockBoxRef.setTranslationY(-dp(30));
            clockBoxRef.animate().alpha(1f).translationY(0f).setStartDelay((long) (200 * animScale))
                    .setDuration((long) (600 * animScale)).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // ------------------------------------------------------------ фон
    private void scanWallpapers() {
        wallpapers = new ArrayList<>();
        File d = new File(dir(), "wallpapers");
        File[] fs = d.listFiles();
        if (fs != null) {
            for (File f : fs) {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) wallpapers.add(f);
            }
        }
        Collections.sort(wallpapers);
        Collections.shuffle(wallpapers, new java.util.Random(PROC_SEED));
    }

    private Bitmap decode(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= dm.widthPixels && o.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    private final Runnable nextWall = new Runnable() {
        @Override public void run() {
            if (!wallpapers.isEmpty()) {
                wallIndex = (wallIndex + 1) % wallpapers.size();
                Bitmap bmp = decode(wallpapers.get(wallIndex));
                if (bmp != null) {
                    ImageView in = wallFront ? wallB : wallA;
                    ImageView out = wallFront ? wallA : wallB;
                    in.setImageBitmap(bmp);
                    Frost.update(bmp);
                    topBar.invalidate();
                    long d = (long) (1800 * animScale);
                    in.animate().alpha(1f).setDuration(d).start();
                    out.animate().alpha(0f).setDuration(d).withEndAction(new Runnable() {
                        @Override public void run() { /* звільняємо стару картинку нижче */ }
                    }).start();
                    wallFront = !wallFront;
                    if (kenBurns == null) startKenBurns();
                }
            }
            handler.postDelayed(this, wallpaperMinutes * 60_000L);
        }
    };

    // ------------------------------------------------------------ життєвий цикл
    @Override
    protected void onResume() {
        super.onResume();
        loadConfig();
        scanWallpapers();
        fillTiles();
        handler.removeCallbacks(nextWall);
        if (!wallpapers.isEmpty()) handler.post(nextWall); else generateWallpapersIfNeeded();
        lastInteract = SystemClock.uptimeMillis();
        updateGreeting();
        if (getIntent() != null && getIntent().getBooleanExtra("apps", false)) {
            getIntent().removeExtra("apps");
            handler.post(new Runnable() { @Override public void run() { openAllApps(); } });
        }
        startOrbs();
        registerStatus();
        startWeather();
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(nextWall);
        handler.removeCallbacks(weatherTick);
        stopOrbs();
        stopKenBurns();
        unregisterStatus();
        if (ambient) {
            ambient = false;
            scroll.setAlpha(1f);
            topBar.setAlpha(1f);
            greetTv.setAlpha(1f);
            ambientBox.setAlpha(0f);
            ambientBox.setVisibility(View.GONE);
        }
        introPending = true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean wantApps = intent != null && intent.getBooleanExtra("apps", false);
        boolean wasApps = appsOpen;
        if (ambient) exitAmbient();
        if (panelOpen) closeQuickPanel();
        if (appsOpen) closeAllApps();
        scroll.scrollTo(0, 0);
        View f = rowsBox.findViewWithTag("card");
        if (f != null) f.requestFocus();
        if (wantApps && !wasApps) handler.post(new Runnable() { @Override public void run() { openAllApps(); } });
    }

    @Override
    public void onBackPressed() {
        if (appsOpen) { closeAllApps(); return; }
        if (panelOpen) {
            if (pwMode) { pwMode = false; needFocus = true; wifiSig = ""; refreshWifiList(); return; }
            closeQuickPanel();
            return;
        }
        // на головному екрані «Назад» більше нічого не робить
    }

    private void updateGreeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        greetTv.setText(h < 5 ? "Доброї ночі" : h < 12 ? "Доброго ранку" : h < 18 ? "Добрий день" : h < 23 ? "Добрий вечір" : "Доброї ночі");
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
