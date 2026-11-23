package pl.mariusz.kalendarzwydarzen;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wspólne pobieranie dla apki i synchronizacji w tle.
 * Treść odpowiedzi NIE jest wklejana do JavaScriptu (duże JSON-y ESPN potrafiły zabić renderer WebView):
 * strona dostaje tylko sygnał window.onHttp(id, status) i odbiera treść przez Android.body(id).
 */
final class Net {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final ConcurrentHashMap<Integer, String> BODIES = new ConcurrentHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Net() { }

    static void get(final WebView web, final int id, final String url) {
        POOL.execute(() -> {
            int code;
            String body;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) KalendarzWydarzen/0.1");
                c.setRequestProperty("Accept", "application/json, text/calendar, text/html, */*");
                code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                if (in != null) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    in.close();
                }
                body = bo.toString(StandardCharsets.UTF_8.name());
            } catch (Throwable e) {
                code = 0;
                body = String.valueOf(e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
            BODIES.put(id, body);
            final String js = "window.onHttp&&window.onHttp(" + id + "," + code + ")";
            MAIN.post(() -> { try { web.evaluateJavascript(js, null); } catch (Throwable ignored) { } });
        });
    }

    /** Odbiór treści (jednorazowo). */
    static String take(int id) {
        String b = BODIES.remove(id);
        return b == null ? "" : b;
    }

    /** Zapis ostatniego błędu aplikacji — apka pokaże go po ponownym uruchomieniu. */
    static void installCrashLog(final Context app) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        if (prev instanceof Logger) return;
        Thread.setDefaultUncaughtExceptionHandler(new Logger(app, prev));
    }

    static void note(Context c, String what) {
        Alarms.prefs(c).edit().putString("crash", what + "\n" + new java.util.Date()).commit();
    }

    private static final class Logger implements Thread.UncaughtExceptionHandler {
        private final Context app;
        private final Thread.UncaughtExceptionHandler prev;
        Logger(Context app, Thread.UncaughtExceptionHandler prev) { this.app = app.getApplicationContext(); this.prev = prev; }
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String s = sw.toString();
                note(app, "[" + t.getName() + "] " + (s.length() > 3000 ? s.substring(0, 3000) : s));
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        }
    }
}
