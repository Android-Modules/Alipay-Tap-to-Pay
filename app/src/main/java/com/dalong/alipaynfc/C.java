package com.dalong.alipaynfc;

/** Global constants shared between the app UI and the hooked processes. */
public final class C {
    private C() {}

    public static final String MODULE_PKG = "com.dalong.alipaynfc";
    public static final String ALIPAY_PKG = "com.eg.android.AlipayGphone";
    public static final String NFC_PKG = "com.android.nfc";

    public static final String PREFS = "nfc_config";

    public static final String K_ENABLED = "enabled";
    public static final String K_TARGET_USER = "target_user";
    public static final String K_TARGET_PKG = "target_pkg";
    public static final String K_ALIPAY_PKG = "alipay_pkg";
    public static final String K_KEEP_HISTORY = "keep_history";
    public static final String K_REVISION = "revision";

    public static final String K_USERS_JSON = "users_json";
    public static final String K_ALIVE_JSON = "alive_json";
    public static final String K_HISTORY_JSON = "history_json";

    // Broadcasts between hooked processes and the app.
    public static final String ACTION_HOOK_ALIVE = "com.dalong.alipaynfc.HOOK_ALIVE";
    public static final String ACTION_SCAN_RESULT = "com.dalong.alipaynfc.SCAN_RESULT";
    public static final String ACTION_RECORD = "com.dalong.alipaynfc.RECORD";
    public static final String ACTION_REQUEST_SCAN = "com.dalong.alipaynfc.REQUEST_SCAN";
    public static final String ACTION_REQUEST_CLEAR = "com.dalong.alipaynfc.REQUEST_CLEAR";
    public static final String ACTION_CONFIG_CHANGED = "com.dalong.alipaynfc.CONFIG_CHANGED";

    public static final String EXTRA_JSON = "json";
    public static final String EXTRA_HANDLED = "com.dalong.alipaynfc.handled";

    // Settings.Global keys: reliable IPC channel that survives MIUI background
    // broadcast restrictions (com.android.nfc holds WRITE_SECURE_SETTINGS).
    public static final String GKEY_ALIVE = "alipaynfc_alive";
    public static final String GKEY_USERS = "alipaynfc_users";
    public static final String GKEY_RECORDS = "alipaynfc_records";

    // NFC tag dispatch actions.
    public static final String ACTION_NDEF = "android.nfc.action.NDEF_DISCOVERED";
    public static final String ACTION_TAG = "android.nfc.action.TAG_DISCOVERED";
    public static final String ACTION_TECH = "android.nfc.action.TECH_DISCOVERED";
    public static final String ACTION_TRANSACTION = "android.nfc.action.TRANSACTION_DETECTED";
}
