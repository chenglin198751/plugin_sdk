package com.plugin.sdk.plugin;

import android.content.Context;
import android.view.View;

/**
 * 插件入口类。
 * <p>
 * 宿主通过反射加载插件 dex 后，反射调用本类的静态方法。
 * 本类只依赖 android.jar，不依赖任何宿主类，保证插件可以独立编译。
 * <p>
 * 插件资源由 AGP 正常编译（packageId 默认 0x7f，不是 0x00 占位），
 * {@link PluginResources} 用 {@code addAssetPath} 构造独立 Resources 来隔离，
 * 不需要调用 {@code R.onResourcesLoaded}。
 */
public final class PluginEntry {

    /**
     * 插件版本号（唯一真源）。
     * <p>
     * {@code plugin/build.gradle} 的 versionCode / versionName 会从这里解析，保证 APK
     * 元数据版本与 dex 内常量一致（三码校验的基础）。改插件版本只改这里两个常量即可。
     */
    public static final String PLUGIN_VERSION_NAME = "1.0.0";
    public static final int PLUGIN_VERSION_CODE = 100;

    private PluginEntry() {
    }

    /**
     * 插件版本号，宿主可用来展示 / 对比是否需要更新。
     */
    public static String getVersion() {
        return PLUGIN_VERSION_NAME;
    }

    /**
     * 插件版本码（整数），宿主用来做三码校验和降级排序。
     */
    public static int getVersionCode() {
        return PLUGIN_VERSION_CODE;
    }

    /**
     * 初始化插件资源：加载插件 APK 资源。
     * 宿主在加载插件 dex 后调用一次（幂等）。
     */
    public static void initResources(Context context, String pluginPath) {
        PluginResources.init(context, pluginPath);
    }

    /**
     * 宿主直接拿一个插件里的 View 嵌入显示（演示「dex + 资源」整条链路）。
     * <p>
     * 建议传 Activity 而不是 Application，以便插件 View 拿到正确的主题。
     */
    public static View createView(Context context) {
        return PluginResources.inflate(context, R.layout.plugin_activity);
    }
}
