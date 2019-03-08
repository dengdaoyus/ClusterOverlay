package com.amap.apis.cluster;

import android.app.Application;

public class BaseApp extends Application {

    private static BaseApp instance;
    public static BaseApp getInstance() {
        return instance;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}
