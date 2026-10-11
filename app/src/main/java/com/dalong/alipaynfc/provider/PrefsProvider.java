package com.dalong.alipaynfc.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.dalong.alipaynfc.C;
import com.dalong.alipaynfc.data.Store;

/**
 * Fallback config channel: hooked processes may read the module config through this
 * provider when XSharedPreferences is unavailable (e.g. SELinux denials).
 */
public class PrefsProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (getContext() == null) return null;
        if ("getConfig".equals(method)) {
            return configBundle();
        }
        if ("setConfig".equals(method) && extras != null) {
            // Debug helper: allows adb to set config during automated testing.
            Store.Config c = Store.loadConfig(getContext());
            if (extras.containsKey(C.K_TARGET_USER)) c.targetUser = extras.getInt(C.K_TARGET_USER);
            if (extras.containsKey(C.K_ENABLED)) c.enabled = extras.getBoolean(C.K_ENABLED);
            if (extras.containsKey(C.K_TARGET_PKG)) c.targetPkg = extras.getString(C.K_TARGET_PKG);
            if (extras.containsKey(C.K_KEEP_HISTORY)) c.keepHistory = extras.getBoolean(C.K_KEEP_HISTORY);
            Store.saveConfig(getContext(), c);
            android.content.Intent i = new android.content.Intent(C.ACTION_CONFIG_CHANGED);
            getContext().sendBroadcast(i);
            return configBundle();
        }
        return null;
    }

    private Bundle configBundle() {
        Bundle b = new Bundle();
        Store.Config c = Store.loadConfig(getContext());
        b.putBoolean(C.K_ENABLED, c.enabled);
        b.putInt(C.K_TARGET_USER, c.targetUser);
        b.putString(C.K_TARGET_PKG, c.targetPkg);
        b.putString(C.K_ALIPAY_PKG, c.alipayPkg);
        b.putBoolean(C.K_KEEP_HISTORY, c.keepHistory);
        b.putLong(C.K_REVISION, Store.revision(getContext()));
        return b;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
