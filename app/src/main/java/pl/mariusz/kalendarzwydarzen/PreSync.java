package pl.mariusz.kalendarzwydarzen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Budzik „30 min przed wydarzeniem” → jednorazowa synchronizacja w tle. Kolejny budzik ustawi sama synchronizacja. */
public class PreSync extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try { BgSync.runOnce(c); } catch (Throwable t) { Net.note(c, "PreSync: " + t); }
    }
}
