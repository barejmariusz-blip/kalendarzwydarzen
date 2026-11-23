package pl.mariusz.kalendarzwydarzen;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Synchronizacja w tle co ~3 h (JobScheduler, tylko z internetem).
 * Uruchamia tę samą logikę co apka (assets/index.html?bg=1) w niewidocznym WebView:
 * pobiera terminy, sprawdza Polaków / składy, wykrywa zmiany i wysyła powiadomienia,
 * przelicza przypomnienia i odświeża widżet. Gdy apka jest na ekranie — nic nie robi.
 */
public class BgSync extends JobService {

    static final int JOB_ID = 4242;
    static volatile boolean appVisible = false;
    private static int notifSlot = 0;

    private WebView web;
    private JobParameters params;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newFixedThreadPool(3);

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, BgSync.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(3 * 60 * 60 * 1000L, 30 * 60 * 1000L)
                .setPersisted(true)
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        if (appVisible) return false;
        params = p;
        try {
            Net.installCrashLog(this);
            web = new WebView(getApplicationContext());
            web.setWebViewClient(new android.webkit.WebViewClient() {
                @Override
                public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                    Net.note(BgSync.this, "Tło: WebView renderer gone (crash=" + detail.didCrash() + ")");
                    web = null;   // już nieużywalny
                    finish(false);
                    return true;
                }
            });
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(true);
            web.addJavascriptInterface(new Bridge(), "Android");
            web.loadUrl("file:///android_asset/index.html?bg=1");
            main.postDelayed(() -> finish(true), 8 * 60 * 1000L);   // bezpiecznik
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) {
        cleanup();
        return true;
    }

    private void finish(boolean reschedule) {
        main.post(() -> {
            if (params == null) return;
            JobParameters p = params;
            params = null;
            cleanup();
            jobFinished(p, reschedule);
        });
    }

    private void cleanup() {
        main.removeCallbacksAndMessages(null);
        if (web != null) { try { web.destroy(); } catch (Exception ignored) { } web = null; }
    }

    private void js(String code) {
        main.post(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    class Bridge {
        @JavascriptInterface
        public void http(final int id, final String url) { if (web != null) Net.get(web, id, url); }

        @JavascriptInterface
        public String body(int id) { return Net.take(id); }

        @JavascriptInterface
        public void schedule(String json) { Alarms.scheduleAll(BgSync.this, json); }

        @JavascriptInterface
        public void lineupWatch(String json) { Lineups.scheduleAll(BgSync.this, json); }

        @JavascriptInterface
        public void widget(String json) { EventsWidget.save(BgSync.this, json); }

        @JavascriptInterface
        public void notifyNow(String title, String body) { Alarms.notify(BgSync.this, 100 + (notifSlot++ % 40), title, body); }

        @JavascriptInterface
        public String notifyState() { return "granted"; }

        @JavascriptInterface
        public boolean exactOk() { return true; }

        @JavascriptInterface
        public void bgDone() { finish(false); }
    }
}
