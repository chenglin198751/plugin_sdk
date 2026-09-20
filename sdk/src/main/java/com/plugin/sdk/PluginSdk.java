package com.plugin.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.plugin.sdk.activity.HostProxyActivity;
import com.plugin.sdk.hotupdate.HotUpdateEngine;
import com.plugin.sdk.hotupdate.PluginRepository;
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
 * 之后通过 {@link #startPluginActivity} 启动插件，或通过 {@link #importPlugin} 导入新插件。
 * <p>
 * 插件页面本身不是 android.app.Activity，无需在宿主 Manifest 中声明。
 */
public final class PluginSdk {

    /** 默认插件页面标识。 */
    public static final int DEFAULT_PLUGIN_VIEW_ID = 1;

    /** Intent extra：插件路径。 */
    private static final String EXTRA_PLUGIN_PATH = "plugin_path";

    /** Intent extra：插件页面标识，与插件侧 ApkProxyActivity.EXTRA_VIEW_ID 的取值约定一致。 */
    private static final String EXTRA_PLUGIN_VIEW_ID = "plugin_view_id";

    private PluginSdk() {
    }

    /**
     * 插件加载完成回调，运行在主线程。
     */
    public interface LoadCallback {
        /**
         * 插件加载完成。
         *
         * @param success 是否加载成功
         * @param version 成功时为插件版本，失败时为 null
         * @param error   失败原因，成功时为 null
         */
        void onLoadFinished(boolean success, String version, String error);
    }

    /** 接入方在 Application.onCreate 里调用一次，初始化热更新引擎（异步，无回调）。 */
    public static void init(Application app) {
        init(app, null);
    }

    /**
     * 初始化热更新引擎（异步）。
     * <p>
     * 调用后立即返回，插件在后台线程加载；加载完成后在主线程回调 {@code callback}。
     * 未传回调时可改用 {@link #isPluginLoaded()} 查询加载状态。
     */
    public static void init(Application app, LoadCallback callback) {
        HotUpdateEngine.init(app, callback);
    }

    public static boolean isPluginLoaded() {
        HotUpdateEngine engine = HotUpdateEngine.get();
        return engine != null && engine.isPluginLoaded();
    }

    public static String getPluginVersion() {
        HotUpdateEngine engine = HotUpdateEngine.get();
        return engine == null ? null : engine.getPluginVersion();
    }

    public static String getPluginPath() {
        HotUpdateEngine engine = HotUpdateEngine.get();
        return engine == null ? null : engine.getPluginPath();
    }

    public static String getLastError() {
        HotUpdateEngine engine = HotUpdateEngine.get();
        return engine == null ? null : engine.getLastError();
    }

    /**
     * 从 /sdcard/Download/plugin_main.apk 导入插件，返回插件路径（需重启生效）。
     * <p>
     * 注意：targetSdk 30 起应用无法用文件路径读取 Download 目录下的非媒体文件，
     * 该方法在 Android 11+ 上会失败。真机验证或生产环境请改用
     * {@link #importPlugin(Context, File)}。
     */
    public static String importPlugin(Context context) throws IOException {
        return PluginRepository.importFromSdcard(context);
    }

    /**
     * 从指定文件导入插件，返回插件路径（需重启生效）。
     * <p>
     * 推荐用法：接入方把插件下载到自己的 filesDir / cacheDir 后调用本方法，
     * 避免受分区存储限制。
     */
    public static String importPlugin(Context context, File pluginFile) throws IOException {
        return PluginRepository.importFromFile(context, pluginFile);
    }

    /**
     * 启动插件 Activity，使用默认页面（viewId = {@link #DEFAULT_PLUGIN_VIEW_ID}）。
     * <p>
     * 实际启动的是宿主的占位 Activity {@link HostProxyActivity}，由它把生命周期
     * 转发给插件里的 ApkProxyActivity（对齐 360 插件方案）。
     * 占位 Activity 默认开启 edge-to-edge 全屏。
     */
    public static void startPluginActivity(Context context) {
        startPluginActivity(context, 2);
    }

    /**
     * 启动指定插件页面。
     * <p>
     * 每次调用都会启动一个新的 {@link HostProxyActivity} 实例，页面栈由 Activity
     * 返回栈承载，因此插件里的多页面跳转可以直接靠系统返回键回退。
     * 具体 viewId 对应哪个插件页面，由插件侧的 ApkProxyActivity 分发决定，
     * 该映射表留在插件 APK 内，可随插件热更。
     *
     * @param viewId 插件页面标识，与插件侧 ApkProxyActivity.EXTRA_VIEW_ID 的约定一致
     */
    public static void startPluginActivity(Context context, int viewId) {
        HotUpdateEngine engine = HotUpdateEngine.get();
        if (engine == null || !engine.isPluginLoaded()) {
            throw new IllegalStateException("插件未加载，无法启动插件 Activity");
        }
        Intent intent = new Intent(context, HostProxyActivity.class);
        intent.putExtra(EXTRA_PLUGIN_PATH, engine.getPluginPath());
        intent.putExtra(EXTRA_PLUGIN_VIEW_ID, viewId);
        if (!(context instanceof Activity)) {
            // 非 Activity Context 启动 Activity 必须带这个 flag，否则会抛
            // AndroidRuntimeException: Calling startActivity() from outside of an
            // Activity context requires the FLAG_ACTIVITY_NEW_TASK flag
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        AppLogUtils.i("PluginSdk", "启动插件 Activity: HostProxyActivity, viewId=" + viewId
                + ", 插件路径=" + engine.getPluginPath());
        context.startActivity(intent);
    }
}
