package pl.mariusz.kalendarzwydarzen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Alarms.restore(c);
        Lineups.restore(c);
        EventsWidget.refresh(c);
        BgSync.schedule(c);
        BgSync.preSyncAt(c, Alarms.prefs(c).getLong("preSyncAt", 0));
    }
}
