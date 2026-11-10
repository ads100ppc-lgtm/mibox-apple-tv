package ua.pc.home;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Місцева погода. Місце визначається за IP (ipwho.is), погода — Open-Meteo (без ключів).
 * Можна задати своє місто в config.json: "lat", "lon", "city".
 */
public final class Weather {
    public interface Listener { void onWeather(String emoji, String temp, String city); }

    private static final String PREFS = "weather";

    private Weather() { }

    /** Показати збережене значення одразу (якщо є). */
    public static boolean cached(Context ctx, Listener l) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.contains("temp")) return false;
        l.onWeather(sp.getString("emoji", ""), sp.getString("temp", ""), sp.getString("city", ""));
        return true;
    }

    /** Оновити з мережі у фоновому потоці. Колбек викликається в тому ж потоці — публікуйте в UI самі. */
    public static void fetch(final Context ctx, final Double lat, final Double lon, final String cityOverride, final Listener l) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    double la, lo;
                    String city = cityOverride;
                    if (lat != null && lon != null) {
                        la = lat; lo = lon;
                    } else {
                        JSONObject g = new JSONObject(get("https://ipwho.is/?fields=success,city,latitude,longitude"));
                        if (!g.optBoolean("success", false)) return;
                        la = g.getDouble("latitude");
                        lo = g.getDouble("longitude");
                        if (city == null || city.isEmpty()) city = g.optString("city", "");
                    }
                    JSONObject w = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + la
                            + "&longitude=" + lo + "&current_weather=true&timezone=auto"));
                    JSONObject cw = w.getJSONObject("current_weather");
                    int code = cw.optInt("weathercode", 0);
                    boolean day = cw.optInt("is_day", 1) == 1;
                    String temp = Math.round(cw.getDouble("temperature")) + "°";
                    String emoji = icon(code, day);
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString("emoji", emoji).putString("temp", temp).putString("city", city == null ? "" : city).apply();
                    l.onWeather(emoji, temp, city == null ? "" : city);
                } catch (Throwable ignored) {
                    // немає мережі — лишається збережене значення
                }
            }
        }, "weather").start();
    }

    private static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code == 1 || code == 2) return day ? "🌤️" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 57) return "🌦️";
        if (code >= 61 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 80 && code <= 82) return "🌧️";
        if (code == 85 || code == 86) return "❄️";
        if (code >= 95) return "⛈️";
        return "🌡️";
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "PcHome/1.0");
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}
