package com.plugin.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.plugin.sdk.activity.HostProxyActivity;
import com.plugin.sdk.hotupdate.HotUpdateEngine;
import com.plugin.sdk.hotupdate.PatchRepository;
import com.plugin.sdk.utils.AppLogUtils;

import java.io.File;
import java.io.IOException;

/**
 * PluginSdk 对外 API —— 接入方「一次接入」的全部入口。
 * <p>
 * 接入方只需要做两件事：
 * <ol>
 *   <li>在 Application.onCreate 里调用 {@link #init(Application)}；</li>
 *   <li>在自己的 AndroidManifest 里注册占位 Activity
 *       {@code com.plugin.sdk.activity.HostProxyActivity}。</li>
 * </ol>
 * 之后通过 {@link #startPluginActivity} 启动插件，或通过 {@link #importPatch} 导入新补丁。
 * <p>
 * 插件页面本身不是 android.app.Activity，无需在宿主 Manifest 中声明。
 */
public final class PluginSdk {

    /** 默认插件页面标识。 */
    public static final int DEFAULT_PLUGIN_VIEW_ID = 1;

    /** Intent extra：补丁路径。 */
    private static final String EXTRA_PATCH_PATH = "patch_path";

    /** Intent extra：插件页面标识，与插件侧 ApkProxyActivity.EXTRA_VIEW_ID 的取值约定一致。 */
    private static final String EXTRA_PLUGIN_VIEW_ID = "plugin_view_id";

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

    /**
     * 从 /sdcard/Download/patch.apk 导入补丁，返回补丁路径（需重启生效）。
     * <p>
     * 注意：targetSdk 30 起应用无法用文件路径读取 Download 目录下的非媒体文件，
     * 该方法在 Android 11+ 上会失败。真机验证或生产环境请改用
     * {@link #importPatch(Context, File)}。
     */
    public static String importPatch(Context context) throws IOException {
        return PatchRepository.importFromSdcard(context);
    }

    /**
     * 从指定文件导入补丁，返回补丁路径（需重启生效）。
     * <p>
     * 推荐用法：接入方把补丁下载到自己的 filesDir / cacheDir 后调用本方法，
     * 避免受分区存储限制。
     */
    public static String importPatch(Context context, File patchFile) throws IOException {
        return PatchRepository.importFromFile(context, patchFile);
    }

    /**
     * 启动插件 Activity，使用默认页面（viewId = {@link #DEFAULT_PLUGIN_VIEW_ID}）。
     * <p>
     * 实际启动的是宿主的占位 Activity {@link HostProxyActivity}，由它把生命周期
     * 转发给插件里的 ApkProxyActivity（对齐 360 插件方案）。
     * 占位 Activity 默认开启 edge-to-edge 全屏。
     */
    public static void startPluginActivity(Context context) {
        startPluginActivity(context, DEFAULT_PLUGIN_VIEW_ID);
    }

    /**
     * 启动指定插件页面。
     * <p>
     * 每次调用都会启动一个新的 {@link HostProxyActivity} 实例，页面栈由 Activity
     * 返回栈承载，因此插件里的多页面跳转可以直接靠系统返回键回退。
     * 具体 viewId 对应哪个插件页面，由插件侧的 ApkProxyActivity 分发决定，
     * 该映射表留在插件 APK 内，可随补丁热更。
     *
     * @param viewId 插件页面标识，与插件侧 ApkProxyActivity.EXTRA_VIEW_ID 的约定一致
     */
    public static void startPluginActivity(Context context, int viewId) {
        HotUpdateEngine engine = HotUpdateEngine.get();
        if (engine == null || !engine.isPatchLoaded()) {
            throw new IllegalStateException("补丁未加载，无法启动插件 Activity");
        }
        Intent intent = new Intent(context, HostProxyActivity.class);
        intent.putExtra(EXTRA_PATCH_PATH, engine.getPatchPath());
        intent.putExtra(EXTRA_PLUGIN_VIEW_ID, viewId);
        if (!(context instanceof Activity)) {
            // 非 Activity Context 启动 Activity 必须带这个 flag，否则会抛
            // AndroidRuntimeException: Calling startActivity() from outside of an
            // Activity context requires the FLAG_ACTIVITY_NEW_TASK flag
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        AppLogUtils.i("PluginSdk", "启动插件 Activity: HostProxyActivity, viewId=" + viewId
                + ", 补丁路径=" + engine.getPatchPath());
        context.startActivity(intent);
    }
}
