package pl.mariusz.kalendarzwydarzen;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Sprawdzanie składów meczowych w tle (bez otwierania apki).
 * Strona planuje: {at, url (ESPN summary), ids {athleteId: nazwisko}, title, start}.
 * O czasie „at” pobieramy skład; jeśli Polak jest w wyjściowym składzie — powiadomienie.
 * Skład jeszcze nieogłoszony → ponowna próba za 10 min (do startu meczu).
 */
public class Lineups extends BroadcastReceiver {

    private static final int MAX = 24, BASE = 500;

    static void scheduleAll(Context c, String json) {
        Alarms.prefs(c).edit().putString("lineups", json == null ? "[]" : json).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < MAX; i++) am.cancel(pending(c, i, null));
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            long now = System.currentTimeMillis();
            int slot = 0;
            for (int i = 0; i < arr.length() && slot < MAX; i++) {
                JSONObject a = arr.getJSONObject(i);
                long at = a.getLong("at");
                if (at <= now) continue;
                set(am, at, pending(c, slot++, a.toString()));
            }
        } catch (Exception ignored) { }
    }

    static void restore(Context c) { scheduleAll(c, Alarms.prefs(c).getString("lineups", "[]")); }

    private static void set(AlarmManager am, long at, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    private static PendingIntent pending(Context c, int slot, String payload) {
        Intent i = new Intent(c, Lineups.class).setAction("kw.lineup." + slot);
        if (payload != null) i.putExtra("p", payload).putExtra("slot", slot);
        return PendingIntent.getBroadcast(c, BASE + slot, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(final Context c, final Intent intent) {
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { check(c, intent); } catch (Exception ignored) { }
            finally { pr.finish(); }
        }).start();
    }

    private void check(Context c, Intent intent) throws Exception {
        String p = intent.getStringExtra("p");
        int slot = intent.getIntExtra("slot", 0);
        if (p == null) return;
        JSONObject a = new JSONObject(p);
        String url = a.getString("url");
        JSONObject ids = a.getJSONObject("ids");
        long start = a.optLong("start", 0);

        // Już powiadomiono o tym meczu? (dwa alarmy: -60 i -25 min)
        String doneKey = "lu_done_" + url;
        if (Alarms.prefs(c).getBoolean(doneKey, false)) return;

        JSONObject j = new JSONObject(get(url));
        List<String> starters = new ArrayList<>(), bench = new ArrayList<>();
        boolean published = false;

        JSONArray rosters = j.optJSONArray("rosters");
        if (rosters != null) for (int t = 0; t < rosters.length(); t++) {
            JSONArray r = rosters.getJSONObject(t).optJSONArray("roster");
            if (r == null) continue;
            for (int k = 0; k < r.length(); k++) {
                JSONObject pl = r.getJSONObject(k);
                JSONObject ath = pl.optJSONObject("athlete");
                if (ath == null) continue;
                published = true;
                String id = ath.optString("id");
                if (ids.has(id)) (pl.optBoolean("starter") ? starters : bench).add(ids.getString(id));
            }
        }
        JSONObject box = j.optJSONObject("boxscore");
        JSONArray players = box == null ? null : box.optJSONArray("players");
        if (players != null) for (int t = 0; t < players.length(); t++) {
            JSONArray st = players.getJSONObject(t).optJSONArray("statistics");
            if (st == null) continue;
            for (int s = 0; s < st.length(); s++) {
                JSONArray at = st.getJSONObject(s).optJSONArray("athletes");
                if (at == null) continue;
                for (int k = 0; k < at.length(); k++) {
                    JSONObject pl = at.getJSONObject(k);
                    JSONObject ath = pl.optJSONObject("athlete");
                    if (ath == null) continue;
                    published = true;
                    String id = ath.optString("id"), name = ids.optString(id, null);
                    if (name != null && !starters.contains(name) && !bench.contains(name)) (pl.optBoolean("starter") ? starters : bench).add(name);
                }
            }
        }

        if (!published) {
            // Skład jeszcze nieogłoszony: spróbuj ponownie za 10 minut, ale nie po starcie meczu
            long next = System.currentTimeMillis() + 10 * 60000L;
            if (start == 0 || next < start) {
                AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                if (am != null) set(am, next, pending(c, slot, p));
            }
            return;
        }

        Alarms.prefs(c).edit().putBoolean(doneKey, true).apply();
        if (starters.isEmpty()) return;   // tylko potwierdzone: Polak w wyjściowym składzie
        String time = start > 0 ? new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(start)) : "";
        Alarms.notify(c, 60 + slot, "🇵🇱 ✔ " + join(starters) + " w wyjściowym składzie",
                a.optString("title") + (time.isEmpty() ? "" : " · start " + time)
                        + (bench.isEmpty() ? "" : " · na ławce: " + join(bench)));
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        Iterator<String> it = l.iterator();
        while (it.hasNext()) { b.append(it.next()); if (it.hasNext()) b.append(", "); }
        return b.toString();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) KalendarzWydarzen/0.1");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }
}
