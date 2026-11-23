package pl.mariusz.kalendarzwydarzen;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Widżet: 4 najbliższe wydarzenia (wg trybu z apki) + gdzie oglądać. Dane wystawia strona przez Android.widget(json). */
public class EventsWidget extends AppWidgetProvider {

    private static final int[] ROW = {R.id.r0, R.id.r1, R.id.r2, R.id.r3};
    private static final int[] COL = {R.id.c0, R.id.c1, R.id.c2, R.id.c3};
    private static final int[] TIM = {R.id.t0, R.id.t1, R.id.t2, R.id.t3};
    private static final int[] NAM = {R.id.n0, R.id.n1, R.id.n2, R.id.n3};
    private static final int[] LIN = {R.id.l0, R.id.l1, R.id.l2, R.id.l3};
    private static final String[] DN = {"nd", "pon", "wt", "śr", "czw", "pt", "sob"};

    static void save(Context c, String json) {
        Alarms.prefs(c).edit().putString("widget", json).apply();
        refresh(c);
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, EventsWidget.class));
        if (ids.length > 0) for (int id : ids) m.updateAppWidget(id, build(c));
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_events);
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 900, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        for (int r : ROW) v.setViewVisibility(r, View.GONE);
        int shown = 0;
        try {
            JSONObject j = new JSONObject(Alarms.prefs(c).getString("widget", "{}"));
            v.setTextViewText(R.id.mode, j.optString("mode", ""));
            JSONArray items = j.optJSONArray("items");
            long now = System.currentTimeMillis();
            if (items != null) for (int i = 0; i < items.length() && shown < ROW.length; i++) {
                JSONObject e = items.getJSONObject(i);
                if (e.optLong("end") < now) continue;   // minione odpadają same, bez otwierania apki
                long start = e.optLong("start");
                v.setViewVisibility(ROW[shown], View.VISIBLE);
                v.setTextViewText(TIM[shown], when(start, e.optBoolean("allDay"), now));
                v.setTextViewText(NAM[shown], e.optString("title"));
                String line = e.optString("line");
                v.setTextViewText(LIN[shown], line);
                v.setViewVisibility(LIN[shown], line.isEmpty() ? View.GONE : View.VISIBLE);
                try { v.setInt(COL[shown], "setBackgroundColor", Color.parseColor(e.optString("color", "#FF8A5B"))); } catch (Exception ignored) { }
                shown++;
            }
        } catch (Exception ignored) { }
        v.setViewVisibility(R.id.empty, shown == 0 ? View.VISIBLE : View.GONE);
        return v;
    }

    private static String when(long t, boolean allDay, long now) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        a.setTimeInMillis(now); b.setTimeInMillis(t);
        int dd = (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 400 + b.get(Calendar.DAY_OF_YEAR) - a.get(Calendar.DAY_OF_YEAR);
        String day = dd <= 0 ? "Dziś" : dd == 1 ? "Jutro" : DN[b.get(Calendar.DAY_OF_WEEK) - 1] + " " + new SimpleDateFormat("d.MM", Locale.getDefault()).format(new Date(t));
        if (t < now) return "Trwa";
        return allDay ? day : day + "\n" + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }
}
