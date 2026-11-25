package com.dalong.alipaynfc.xp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.text.TextUtils;

import com.dalong.alipaynfc.C;
import com.dalong.alipaynfc.xp.HookConfig.Cache;

import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Redirects Alipay tag dispatch inside the system NFC process. */
public final class NfcHook {

    private static final String ROOT_ACTIVITY = "com.android.nfc.NfcRootActivity";
    private static final String CHOOSER_ACTIVITY = "com.android.nfc.TechListChooserActivity";
    private static final String FORWARD_STATE = C.MODULE_PKG + ".forwardState";

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static volatile Context appContext;
    private static volatile Handler worker;

    private NfcHook() {}

    public static void init(String pkg, ClassLoader cl) {
        if (!initialized.compareAndSet(false, true)) return;
        XLog.i("loaded in " + pkg + " sdk=" + Build.VERSION.SDK_INT
                + " modern=" + HookRuntime.isModern());
        if (installDispatchHook(cl)) {
            attachApp();
        }
    }

    private static void attachApp() {
        try {
            HookRuntime.hookAllMethods(Application.class, "attach", new HookRuntime.MethodCallback() {
                @Override
                protected void after(HookRuntime.HookParam param) {
                    try {
                        if (param.args.length > 0 && param.args[0] instanceof Context
                                && appContext == null) {
                            appContext = (Context) param.args[0];
                            Context ctx = appContext;
                            HandlerThread thread = new HandlerThread("AlipayNfcDispatch");
                            thread.start();
                            Handler handler = new Handler(thread.getLooper());
                            registerConfigListener(ctx, handler);
                            handler.post(() -> {
                                HookConfig.refresh(ctx, false);
                                sendAlive(ctx, C.NFC_PKG);
                            });
                            worker = handler;
                        }
                    } catch (Throwable t) {
                        XLog.e(t);
                    }
                }
            });
        } catch (Throwable t) {
            XLog.e("attachApp hook failed");
            XLog.e(t);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void registerConfigListener(Context ctx, Handler handler) {
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(C.ACTION_CONFIG_CHANGED);
            f.addAction(C.ACTION_REQUEST_CLEAR);
            f.addAction(Intent.ACTION_USER_UNLOCKED);
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (C.ACTION_CONFIG_CHANGED.equals(action) || Intent.ACTION_USER_UNLOCKED.equals(action)) {
                        HookConfig.refresh(ctx, C.ACTION_CONFIG_CHANGED.equals(action));
                    } else if (C.ACTION_REQUEST_CLEAR.equals(action)) {
                        GStore.clearRecords(context);
                        XLog.i("records cleared via nfc process");
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(r, f, null, handler, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(r, f, null, handler);
            }
        } catch (Throwable t) {
            XLog.e("registerConfigListener failed");
            XLog.e(t);
        }
    }

    private static boolean installDispatchHook(ClassLoader cl) {
        if (Build.VERSION.SDK_INT >= 37) {
            try {
                // Both direct NFC dispatch and a confirmed NFC notification reach this call.
                HookRuntime.findAndHookMethod(
                        Activity.class,
                        "startActivityAsUser",
                        new Class<?>[]{Intent.class, Bundle.class, UserHandle.class},
                        forwardHook);
                XLog.i("dispatch hook installed on NFC activity forwarding");
                return true;
            } catch (Throwable t) {
                XLog.e("NFC activity forwarding hook unavailable");
                XLog.e(t);
            }
        }
        try {
            Class<?> contextImpl = Reflect.findClass("android.app.ContextImpl", cl);
            if (HookRuntime.hookAllMethods(contextImpl, "startActivityAsUser", legacyHook).isEmpty()) {
                XLog.e("ContextImpl.startActivityAsUser not found");
                return false;
            }
            XLog.i("dispatch hook installed on ContextImpl.startActivityAsUser");
            return true;
        } catch (Throwable t) {
            XLog.e("installDispatchHook failed");
            XLog.e(t);
            return false;
        }
    }

    private static final class ForwardState {
        final Intent originalIntent;
        final UserHandle originalUser;
        final Cache config;

        ForwardState(Intent originalIntent, UserHandle originalUser, Cache config) {
            this.originalIntent = originalIntent;
            this.originalUser = originalUser;
            this.config = config;
        }
    }

    private static final HookRuntime.MethodCallback forwardHook = new HookRuntime.MethodCallback() {
        @Override
        protected void before(HookRuntime.HookParam param) {
            if (param.thisObject == null) return;
            String activity = param.thisObject.getClass().getName();
            boolean chooser = CHOOSER_ACTIVITY.equals(activity);
            if (!chooser && !ROOT_ACTIVITY.equals(activity)) return;
            Intent intent = (Intent) param.args[0];
            if (intent == null || !isNfcForward(intent)) return;

            Context ctx = (Context) param.thisObject;
            Cache cfg = HookConfig.get(ctx, worker);
            if (!cfg.enabled || !isAlipayDispatch(intent, cfg)) return;
            if (chooser && intent.getComponent() == null) return;

            UserHandle originalUser = (UserHandle) param.args[2];
            ForwardState state = new ForwardState(intent, originalUser, cfg);
            if (hasTarget(cfg)) {
                Intent redirected = redirectIntent(intent, cfg);
                if (redirected.getComponent() == null && redirected.getPackage() == null) {
                    redirected.setPackage(alipayPackage(cfg));
                }
                UserHandle user = userHandleOf(cfg.targetUser);
                param.args[0] = redirected;
                param.args[2] = user;
            }
            param.setObjectExtra(FORWARD_STATE, state);
        }

        @Override
        protected void after(HookRuntime.HookParam param) {
            ForwardState state = (ForwardState) param.getObjectExtra(FORWARD_STATE);
            if (state == null) return;
            String target = targetLabel(state.config);
            Throwable failure = param.getThrowable();
            if (hasTarget(state.config)
                    && (failure instanceof ActivityNotFoundException || failure instanceof SecurityException)) {
                param.args[0] = state.originalIntent;
                param.args[2] = state.originalUser;
                try {
                    param.setResult(param.invokeOriginal());
                } catch (InvocationTargetException e) {
                    param.setThrowable(e.getCause());
                } catch (Throwable t) {
                    param.setThrowable(t);
                }
                target = "system default (redirect failed)";
            }
            if (param.getThrowable() == null) {
                record(appContext, state.originalIntent, state.config, target);
            }
        }
    };

    private static final HookRuntime.MethodCallback legacyHook = new HookRuntime.MethodCallback() {
        @Override
        protected void before(HookRuntime.HookParam param) {
            try {
                Object[] args = param.args;
                if (args == null) return;
                int intentIdx = -1;
                int userIdx = -1;
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a instanceof Intent && intentIdx < 0) intentIdx = i;
                    else if (a instanceof UserHandle && userIdx < 0) userIdx = i;
                }
                if (intentIdx < 0) return;
                Intent outer = (Intent) args[intentIdx];

                // Only NfcRootActivity / TechListChooserActivity root intents carry "launchIntent".
                ComponentName cn = outer.getComponent();
                if (cn == null || !C.NFC_PKG.equals(cn.getPackageName())) return;
                Intent inner = outer.getParcelableExtra("launchIntent");
                if (inner == null || !isNfcAction(inner.getAction())) return;

                Context ctx = appContext != null ? appContext
                        : (param.thisObject instanceof Context ? (Context) param.thisObject : null);
                if (outer.getBooleanExtra(C.EXTRA_HANDLED, false)) return;
                Cache cfg = HookConfig.get(ctx, worker);
                if (!cfg.enabled || !isAlipayDispatch(inner, cfg)) return;

                if (!hasTarget(cfg)) {
                    outer.putExtra(C.EXTRA_HANDLED, true);
                    record(ctx, inner, cfg, targetLabel(cfg));
                    return;
                }

                redirectLegacy(param, outer, inner, cfg, userIdx);
            } catch (Throwable t) {
                XLog.e("redirectHook error");
                XLog.e(t);
            }
        }
    };

    static boolean isNfcAction(String action) {
        return C.ACTION_NDEF.equals(action) || C.ACTION_TAG.equals(action)
                || C.ACTION_TECH.equals(action) || C.ACTION_TRANSACTION.equals(action);
    }

    private static boolean isNfcForward(Intent intent) {
        return isNfcAction(intent.getAction()) || (Intent.ACTION_VIEW.equals(intent.getAction())
                && (intent.hasExtra(NfcAdapter.EXTRA_TAG) || intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)));
    }

    static boolean isAlipayDispatch(Intent intent, Cache cfg) {
        String alipayPkg = alipayPackage(cfg);
        ComponentName cn = intent.getComponent();
        String pkg = cn == null ? intent.getPackage() : cn.getPackageName();
        if (pkg != null && !alipayPkg.equals(pkg)) return false;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri == null || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) return false;
            String host = uri.getHost();
            if (host == null) return false;
            String domain = host.toLowerCase(Locale.ROOT);
            return domain.equals("alipay.com") || domain.endsWith(".alipay.com")
                    || domain.equals("alipay.cn") || domain.endsWith(".alipay.cn")
                    || domain.equals("alipayplus.com") || domain.endsWith(".alipayplus.com")
                    || domain.equals("macaupass.com") || domain.endsWith(".macaupass.com")
                    || ("https".equalsIgnoreCase(uri.getScheme()) && domain.equals("nfc"));
        }
        return pkg != null || looksLikeAlipay(intent.getDataString())
                || looksLikeAlipay(intent.getType());
    }

    private static String alipayPackage(Cache cfg) {
        return TextUtils.isEmpty(cfg.alipayPkg) ? C.ALIPAY_PKG : cfg.alipayPkg;
    }

    private static boolean looksLikeAlipay(String s) {
        if (s == null) return false;
        String d = s.toLowerCase(Locale.ROOT);
        return d.startsWith("alipays://") || d.startsWith("alipay:")
                || d.contains("alipay.com") || d.contains("alipay.cn")
                || d.contains("alipayplus.com") || d.contains("alipayobjects")
                || d.contains("macaupass.com") || d.contains("com.alipay")
                || d.startsWith("https://nfc/");
    }

    private static boolean hasTarget(Cache cfg) {
        return cfg.targetUser != 0 || !TextUtils.isEmpty(cfg.targetPkg);
    }

    private static String targetLabel(Cache cfg) {
        return TextUtils.isEmpty(cfg.targetPkg)
                ? (cfg.targetUser == 0 ? "user 0 (default)" : "user " + cfg.targetUser)
                : "package " + cfg.targetPkg + " (user " + cfg.targetUser + ")";
    }

    private static Intent redirectIntent(Intent original, Cache cfg) {
        Intent intent = new Intent(original).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!TextUtils.isEmpty(cfg.targetPkg)) {
            intent.setComponent(null);
            intent.setPackage(cfg.targetPkg);
        }
        return intent;
    }

    /** HyperOS 3 and earlier use the dispatcher context directly. */
    private static void redirectLegacy(HookRuntime.HookParam param, Intent outer, Intent inner,
            Cache cfg, int userIdx) {
        try {
            Intent ni = redirectIntent(inner, cfg);
            boolean redirectUser = cfg.targetUser != 0;
            UserHandle uh = userHandleOf(cfg.targetUser);
            try {
                Reflect.call(param.thisObject, "startActivityAsUser",
                        new Class<?>[]{Intent.class, Bundle.class, UserHandle.class},
                        ni, null, uh);
                param.setResult(null);
                outer.putExtra(C.EXTRA_HANDLED, true);
            } catch (Throwable directError) {
                outer.putExtra("launchIntent", ni);
                if (redirectUser) {
                    outer.putExtra("launchIntentUserHandle", uh);
                    if (userIdx >= 0) param.args[userIdx] = uh;
                }
                outer.putExtra(C.EXTRA_HANDLED, true);
                XLog.e("direct launch failed (" + directError + "), using rootIntent extras");
            }
            record(appContext, inner, cfg, targetLabel(cfg));
        } catch (Throwable t) {
            XLog.e("redirect failed, falling back to original dispatch");
            XLog.e(t);
        }
    }

    /** UserHandle.of(int) is hidden; fall back to the public constructor. */
    static UserHandle userHandleOf(int userId) {
        try {
            return (UserHandle) Reflect.callStatic(
                    UserHandle.class, "of", new Class<?>[]{int.class}, userId);
        } catch (Throwable t) {
            return Reflect.newInstance(UserHandle.class, new Class<?>[]{int.class}, userId);
        }
    }

    private static void record(Context ctx, Intent intent, Cache cfg, String target) {
        Handler handler = worker;
        if (ctx == null || handler == null) return;
        long timestamp = System.currentTimeMillis();
        String action = intent.getAction();
        String uri = intent.getDataString();
        ComponentName component = intent.getComponent();
        String from = component == null ? uri : component.flattenToString();
        handler.post(() -> {
            XLog.i("nfc dispatch: action=" + action + " -> " + target
                    + " cfg=" + cfg.source + " revision=" + cfg.revision);
            if (!cfg.keepHistory) return;
            try {
                JSONObject o = new JSONObject();
                o.put("ts", timestamp);
                o.put("uri", uri);
                o.put("action", action);
                o.put("from", from);
                o.put("to", target);
                o.put("revision", cfg.revision);
                String json = o.toString();
                GStore.addRecord(ctx, json);
                Intent bi = new Intent(C.ACTION_RECORD);
                bi.setPackage(C.MODULE_PKG);
                bi.putExtra(C.EXTRA_JSON, json);
                ctx.sendBroadcast(bi);
            } catch (Throwable t) {
                XLog.e("record failed");
                XLog.e(t);
            }
        });
    }

    static void sendAlive(Context ctx, String process) {
        try {
            GStore.markAlive(ctx, process);
            JSONObject o = new JSONObject();
            o.put("process", process);
            Intent bi = new Intent(C.ACTION_HOOK_ALIVE);
            bi.setPackage(C.MODULE_PKG);
            bi.putExtra(C.EXTRA_JSON, o.toString());
            ctx.sendBroadcast(bi);
            XLog.i("alive broadcast sent from " + process);
        } catch (Throwable t) {
            XLog.e("sendAlive failed");
            XLog.e(t);
        }
    }
}
