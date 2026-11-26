package com.dalong.alipaynfc.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dalong.alipaynfc.C;
import com.dalong.alipaynfc.R;
import com.dalong.alipaynfc.data.FrameworkState;

/** About Us page featuring author CoolApk, GitHub links, and runtime environment info. */
public class AboutActivity extends Activity {

    private TextView heroSubtitle;
    private TextView envFrameworkText;
    private TextView envStatusText;
    private final FrameworkState.Listener fwListener = this::refreshStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        UiKit.applySystemBars(this);
        refreshStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FrameworkState.addListener(fwListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onStop() {
        FrameworkState.removeListener(fwListener);
        super.onStop();
    }

    private void refreshStatus() {
        UiKit.AliveStatus st = UiKit.readAliveStatus(this);
        String ver = UiKit.getAppVersionName(this);
        String stateLabel = st.allActive ? "框架已启用" : "等待 NFC 注入";
        heroSubtitle.setText("版本 " + ver + " · " + stateLabel);
        String fw = FrameworkState.getFrameworkSummary();
        envFrameworkText.setText("Xposed 框架："
                + (fw != null ? fw : "传统 Xposed API / 未连接框架服务"));
        envStatusText.setText("运行状态：NFC 服务 " + (st.nfcOk ? "✓" : "○")
                + " · 实例扫描 " + (st.scanOk ? "✓" : "○"));
        envStatusText.setTextColor(UiKit.color(this,
                st.allActive ? R.color.status_ok : R.color.text_secondary));
    }

    private View buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setFitsSystemWindows(true);
        outer.setBackgroundColor(UiKit.color(this, R.color.bg_page));

        // Top navigation bar matching ppx's About top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 6),
                UiKit.dp(this, 16), UiKit.dp(this, 6));

        TextView backBtn = new TextView(this);
        backBtn.setText("‹ 返回");
        backBtn.setTextSize(15.5f);
        backBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        backBtn.setTextColor(UiKit.color(this, R.color.brand_primary));
        backBtn.setClickable(true);
        backBtn.setFocusable(true);
        backBtn.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 8),
                UiKit.dp(this, 12), UiKit.dp(this, 8));
        backBtn.setBackground(UiKit.rippleBg(this, Color.TRANSPARENT, UiKit.dpF(this, 10)));
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn);

        TextView pageTitle = new TextView(this);
        pageTitle.setText(R.string.about_title);
        pageTitle.setTextSize(19);
        pageTitle.setTypeface(Typeface.DEFAULT_BOLD);
        pageTitle.setTextColor(UiKit.color(this, R.color.text_primary));
        pageTitle.setPadding(UiKit.dp(this, 4), 0, 0, 0);
        topBar.addView(pageTitle);

        outer.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        outer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 12),
                UiKit.dp(this, 18), UiKit.dp(this, 28));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. App hero row
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        heroRow.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 4),
                UiKit.dp(this, 2), UiKit.dp(this, 4));
        heroRow.addView(UiKit.appIconBadge(this, 56, 16));

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams heroTextLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        heroTextLp.setMarginStart(UiKit.dp(this, 14));

        TextView appName = new TextView(this);
        appName.setText(R.string.app_name);
        appName.setTextSize(22);
        appName.setTypeface(Typeface.DEFAULT_BOLD);
        appName.setTextColor(UiKit.color(this, R.color.text_primary));
        heroText.addView(appName);

        heroSubtitle = new TextView(this);
        heroSubtitle.setTextSize(13.5f);
        heroSubtitle.setTextColor(UiKit.color(this, R.color.text_secondary));
        heroSubtitle.setPadding(0, UiKit.dp(this, 3), 0, 0);
        heroText.addView(heroSubtitle);

        heroRow.addView(heroText, heroTextLp);
        root.addView(heroRow);

        // 2. Author & CoolApk card
        LinearLayout communityCard = UiKit.card(this, UiKit.color(this, R.color.bg_card), 20);
        communityCard.addView(UiKit.linkRow(
                this,
                "酷安",
                "我的馬眼会发光",
                v -> UiKit.openExternal(this, C.URL_COOLAPK, C.PKG_COOLAPK, C.URL_COOLAPK)));
        communityCard.addView(UiKit.divider(this, 16));
        communityCard.addView(UiKit.linkRow(
                this,
                "更多软件",
                "mount.sb996.cn",
                v -> UiKit.openBrowser(this, C.URL_MORE_APPS)));
        root.addView(communityCard, cardMarginTop(16));

        // 3. GitHub & Project card
        LinearLayout githubCard = UiKit.card(this, UiKit.color(this, R.color.bg_card), 20);
        githubCard.addView(UiKit.linkRow(
                this,
                "项目源码",
                "github.com/Android-Modules/Alipay-Tap-to-Pay",
                v -> UiKit.openBrowser(this, C.URL_GITHUB_REPO)));
        githubCard.addView(UiKit.divider(this, 16));
        githubCard.addView(UiKit.linkRow(
                this,
                "问题反馈",
                "GitHub Issues",
                v -> UiKit.openBrowser(this, C.URL_GITHUB_ISSUES)));
        githubCard.addView(UiKit.divider(this, 16));
        githubCard.addView(UiKit.linkRow(
                this,
                "版本下载",
                "GitHub Releases",
                v -> UiKit.openBrowser(this, C.URL_GITHUB_RELEASES)));
        githubCard.addView(UiKit.divider(this, 16));
        githubCard.addView(UiKit.linkRow(
                this,
                "GitHub 主页",
                "github.com/vs2pk0",
                v -> UiKit.openBrowser(this, C.URL_GITHUB_PROFILE)));
        root.addView(githubCard, cardMarginTop(14));

        // 4. Runtime environment card
        LinearLayout envCard = UiKit.card(this, UiKit.color(this, R.color.bg_card), 20);
        envCard.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 16),
                UiKit.dp(this, 16), UiKit.dp(this, 16));

        TextView envTitle = new TextView(this);
        envTitle.setText("运行环境");
        envTitle.setTextSize(15.5f);
        envTitle.setTypeface(Typeface.DEFAULT_BOLD);
        envTitle.setTextColor(UiKit.color(this, R.color.text_primary));
        envCard.addView(envTitle);

        TextView sysInfo = new TextView(this);
        sysInfo.setText("当前系统：Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ") · " + Build.MODEL);
        sysInfo.setTextSize(13.5f);
        sysInfo.setTextColor(UiKit.color(this, R.color.text_primary));
        sysInfo.setPadding(0, UiKit.dp(this, 6), 0, 0);
        envCard.addView(sysInfo);

        TextView scopeInfo = new TextView(this);
        scopeInfo.setText("LSPosed 作用域：NFC (com.android.nfc)");
        scopeInfo.setTextSize(12.5f);
        scopeInfo.setTextColor(UiKit.color(this, R.color.text_secondary));
        scopeInfo.setPadding(0, UiKit.dp(this, 4), 0, 0);
        envCard.addView(scopeInfo);

        envFrameworkText = new TextView(this);
        envFrameworkText.setTextSize(12.5f);
        envFrameworkText.setTextColor(UiKit.color(this, R.color.text_secondary));
        envFrameworkText.setPadding(0, UiKit.dp(this, 4), 0, 0);
        envCard.addView(envFrameworkText);

        envStatusText = new TextView(this);
        envStatusText.setTextSize(12.5f);
        envStatusText.setPadding(0, UiKit.dp(this, 4), 0, 0);
        envCard.addView(envStatusText);

        root.addView(envCard, cardMarginTop(14));

        // 5. Footer caption
        TextView footer = new TextView(this);
        footer.setText("由我的馬眼会发光（vs2pk0）维护 · 支付宝碰一碰切换");
        footer.setTextSize(12);
        footer.setLineSpacing(UiKit.dpF(this, 2), 1f);
        footer.setTextColor(UiKit.color(this, R.color.text_tertiary));
        footer.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 14), UiKit.dp(this, 4), 0);
        root.addView(footer);

        return outer;
    }

    private LinearLayout.LayoutParams cardMarginTop(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiKit.dp(this, topDp);
        return lp;
    }
}
