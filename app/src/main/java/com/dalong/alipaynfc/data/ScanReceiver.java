package com.dalong.alipaynfc.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dalong.alipaynfc.C;

/**
 * Receives broadcasts from the hooked processes (system_server / com.android.nfc):
 * scan results, tap records and hook-alive heartbeats.
 */
public class ScanReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        String json = intent.getStringExtra(C.EXTRA_JSON);
        switch (action) {
            case C.ACTION_SCAN_RESULT:
                if (json != null) Store.saveUsers(context, json);
                break;
            case C.ACTION_RECORD:
                if (json != null) Store.addRecord(context, json);
                break;
            case C.ACTION_HOOK_ALIVE:
                Store.setAlive(context, json == null ? "unknown" : json);
                break;
            default:
                break;
        }
    }
}
