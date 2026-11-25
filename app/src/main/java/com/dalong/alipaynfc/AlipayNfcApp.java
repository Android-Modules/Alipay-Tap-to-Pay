package com.dalong.alipaynfc;

import android.app.Application;

import com.dalong.alipaynfc.data.FrameworkState;

/** Application class initializing the libxposed service listener. */
public class AlipayNfcApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FrameworkState.init(this);
    }
}
