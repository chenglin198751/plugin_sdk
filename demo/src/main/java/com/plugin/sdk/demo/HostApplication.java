package com.plugin.sdk.demo;

import android.app.Application;

import com.plugin.sdk.PluginSdk;

/**
 * demo 的 Application：模拟「接入方」集成 PluginSdk 的方式。
 * <p>
 * 真实接入方只需在自己的 Application 里调用 {@link PluginSdk#init(Application)}，
 * 即可完成「一次接入」，之后插件即可热更新。
 */
public class HostApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        PluginSdk.init(this);
    }
}
