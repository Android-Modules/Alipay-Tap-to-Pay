package com.dalong.alipaynfc.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dalong.alipaynfc.C;
import com.dalong.alipaynfc.R;
import com.dalong.alipaynfc.data.Store;

import org.json.JSONObject;

/** Shared pure-Android UI primitives, theme helpers, and external-link utilities. */
public final class UiKit {

    private UiKit() {}

    public static final class AliveStatus {
        public final boolean nfcOk;
        public final boolean scanOk;
        public final boolean allActive;

        AliveStatus(boolean nfcOk, boolean scanOk) {
            this.nfcOk = nfcOk;
            this.scanOk = scanOk;
            this.allActive = nfcOk;
        }
    }

    public static AliveStatus readAliveStatus(Context ctx) {
        try {
            String raw = Store.getAlive(ctx);
            JSONObject alive = new JSONObject(raw == null || raw.isEmpty() ? "{}" : raw);
            int boot = Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            long bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();
            boolean nfcOk = isAliveThisBoot(alive, C.NFC_PKG, boot, bootTime);
            String users = Store.getUsers(ctx);
            boolean scanOk = users != null && !users.isEmpty();
            return new AliveStatus(nfcOk, scanOk);
        } catch (Exception e) {
            return new AliveStatus(false, false);
        }
    }

    private static boolean isAliveThisBoot(JSONObject alive, String process, int boot, long bootTime) {
        int recordedBoot = alive.optInt(process + "_boot", -1);
        if (boot >= 0 && recordedBoot >= 0) return boot == recordedBoot;
        long heartbeat = alive.optLong(process, 0);
        return heartbeat > 0 && heartbeat >= bootTime;
    }

    public static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    public static float dpF(Context ctx, float value) {
        return value * ctx.getResources().getDisplayMetrics().density;
    }

    public static int color(Context ctx, int resId) {
        return ctx.getColor(resId);
    }

    public static boolean isNightMode(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressWarnings("deprecation")
    public static void applySystemBars(Activity activity) {
        Window window = activity.getWindow();
        View decor = window.getDecorView();
        int bg = color(activity, R.color.bg_page);
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        boolean lightIcons = !isNightMode(activity);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = decor != null ? window.getInsetsController() : null;
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightIcons ? mask : 0, mask);
            }
        } else if (decor != null) {
            int flags = decor.getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if (lightIcons) {
                flags |= lightFlags;
            } else {
                flags &= ~lightFlags;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    public static GradientDrawable roundedRect(int fillColor, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusPx);
        d.setColor(fillColor);
        return d;
    }

    public static Drawable rippleBg(Context ctx, int fillColor, float radiusPx) {
        Drawable content = fillColor == Color.TRANSPARENT ? null : roundedRect(fillColor, radiusPx);
        Drawable mask = roundedRect(Color.WHITE, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(color(ctx, R.color.ripple)), content, mask);
    }

    public static LinearLayout card(Context ctx, int bgColor, int radiusDp) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundedRect(bgColor, dpF(ctx, radiusDp)));
        box.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        box.setClipToOutline(true);
        return box;
    }

    public static TextView sectionLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(color(ctx, R.color.brand_primary));
        tv.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 4));
        return tv;
    }

    public static View divider(Context ctx, int insetDp) {
        View v = new View(ctx);
        v.setBackgroundColor(color(ctx, R.color.divider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(ctx, 1) / 2));
        int inset = dp(ctx, insetDp);
        lp.setMargins(inset, 0, inset, 0);
        v.setLayoutParams(lp);
        return v;
    }

    public static LinearLayout linkRow(Context ctx, String title, String subtitle, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(rippleBg(ctx, Color.TRANSPARENT, 0));
        row.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        row.setOnClickListener(onClick);

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(15.5f);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setTextColor(color(ctx, R.color.text_primary));
        textCol.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subView = new TextView(ctx);
            subView.setText(subtitle);
            subView.setTextSize(13);
            subView.setTextColor(color(ctx, R.color.text_secondary));
            subView.setPadding(0, dp(ctx, 2), 0, 0);
            textCol.addView(subView);
        }

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(ctx);
        chevron.setText("›");
        chevron.setTextSize(20);
        chevron.setTextColor(color(ctx, R.color.text_tertiary));
        chevron.setPadding(dp(ctx, 8), 0, 0, 0);
        row.addView(chevron);

        return row;
    }

    public static FrameLayout appIconBadge(Context ctx, int sizeDp, int cornerDp) {
        int size = dp(ctx, sizeDp);
        int pad = Math.max(dp(ctx, 6), size / 6);
        FrameLayout badge = new FrameLayout(ctx);
        badge.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        badge.setBackground(roundedRect(color(ctx, R.color.bg_accent_soft), dpF(ctx, cornerDp)));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ilp.setMargins(pad, pad, pad, pad);
        badge.addView(icon, ilp);
        return badge;
    }

    public static TextView pillButton(Context ctx, String label, View.OnClickListener onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(label);
        btn.setTextSize(13.5f);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setTextColor(color(ctx, R.color.brand_primary));
        btn.setGravity(Gravity.CENTER);
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setPadding(dp(ctx, 14), dp(ctx, 7), dp(ctx, 14), dp(ctx, 7));
        btn.setBackground(rippleBg(ctx, color(ctx, R.color.bg_accent_soft), dpF(ctx, 20)));
        btn.setOnClickListener(onClick);
        return btn;
    }

    public static TextView textActionButton(Context ctx, String label, View.OnClickListener onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(label);
        btn.setTextSize(13.5f);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setTextColor(color(ctx, R.color.brand_primary));
        btn.setGravity(Gravity.CENTER);
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));
        btn.setBackground(rippleBg(ctx, Color.TRANSPARENT, dpF(ctx, 10)));
        btn.setOnClickListener(onClick);
        return btn;
    }

    public static TextView statusChip(Context ctx, String text, boolean ok) {
        TextView chip = new TextView(ctx);
        chip.setText((ok ? "✓ " : "○ ") + text);
        chip.setTextSize(12);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setTextColor(color(ctx, ok ? R.color.status_ok : R.color.status_warn));
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5));
        chip.setBackground(roundedRect(color(ctx, R.color.bg_chip), dpF(ctx, 10)));
        return chip;
    }

    public static EditText styledInput(Context ctx, String hint, int inputType) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setTextSize(14);
        et.setSingleLine(true);
        et.setTextColor(color(ctx, R.color.text_primary));
        et.setHintTextColor(color(ctx, R.color.text_tertiary));
        et.setPadding(dp(ctx, 14), dp(ctx, 11), dp(ctx, 14), dp(ctx, 11));
        et.setBackground(roundedRect(color(ctx, R.color.bg_input), dpF(ctx, 12)));
        return et;
    }

    public static String getAppVersionName(Context ctx) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            if (info != null && info.versionName != null && !info.versionName.isEmpty()) {
                return info.versionName;
            }
        } catch (Exception ignored) {
        }
        return "1.0.9";
    }

    public static void openBrowser(Context ctx, String url) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, "未找到可打开链接的应用", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx, "无法打开链接", Toast.LENGTH_SHORT).show();
        }
    }

    public static void openExternal(Context ctx, String url, String packageName, String fallbackUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage(packageName);
            ctx.startActivity(intent);
        } catch (Exception e) {
            if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                openBrowser(ctx, fallbackUrl);
            } else {
                Toast.makeText(ctx, "未安装对应应用", Toast.LENGTH_LONG).show();
            }
        }
    }
}
