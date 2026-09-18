package com.plugin.sdk;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.plugin.sdk.activity.HostProxyActivity;
import com.plugin.sdk.hotupdate.HotUpdateEngine;
import com.plugin.sdk.hotupdate.PatchRepository;
import com.plugin.sdk.utils.AppLogUtils;

import java.io.IOException;

/**
 * PluginSdk 对外 API —— 接入方「一次接入」的全部入口。
 * <p>
 * 接入方只需要做两件事：
 * <ol>
 *   <li>在 Application.onCreate 里调用 {@link #init(Application)}；</li>
 *   <li>在自己的 AndroidManifest 里预注册插件 Activity 的类名（如
 *       {@code com.plugin.sdk.plugin.PluginActivity}）。</li>
 * </ol>
 * 之后通过 {@link #startPluginActivity} 启动插件，或通过 {@link #importPatch} 导入新补丁。
 */
public final class PluginSdk {

    private PluginSdk() {
    }

    /** 接入方在 Application.onCreate 里调用一次，初始化热更新引擎。 */
    public static void init(Application app) {
        HotUpdateEngine.init(app);
    }

    public static boolean isPatchLoaded() {
        HotUpdateEngine engine = HotUpdateEngine.get();
        return engine != null && engine.isPatchLoaded();
    }

    public static String getPatchVersion() {
        HotUpdateEngine engine = HotUpdateEngine.get();
        return engine == null ? null : engine.getPatchVersion();
    }

    public static String getPatchPath() {
        HotUpdateEngine engine = HotUpdateEngine.get();
        return engine == null ? null : engine.getPatchPath();
    }

    public static String getLastError() {
        HotUpdateEngine engine = HotUpdateEngine.get();
        return engine == null ? null : engine.getLastError();
    }

    /** 从 /sdcard/Download/patch.apk 导入补丁，返回补丁路径（需重启生效）。 */
    public static String importPatch(Context context) throws IOException {
        return PatchRepository.importFromSdcard(context);
    }

    /**
     * 启动插件 Activity。
     * <p>
     * 实际启动的是宿主的占位 Activity {@link HostProxyActivity}，由它把生命周期
     * 转发给插件里的 ApkProxyActivity（对齐 360 插件方案）。
     */
    public static void startPluginActivity(Context context) {
        HotUpdateEngine engine = HotUpdateEngine.get();
        if (engine == null || !engine.isPatchLoaded()) {
            throw new IllegalStateException("补丁未加载，无法启动插件 Activity");
        }
        Intent intent = new Intent(context, HostProxyActivity.class);
        intent.putExtra("patch_path", engine.getPatchPath());
        intent.putExtra("plugin_view_id", 1);
        AppLogUtils.i("PluginSdk", "启动插件 Activity: HostProxyActivity, 补丁路径=" + engine.getPatchPath());
        context.startActivity(intent);
    }
}
