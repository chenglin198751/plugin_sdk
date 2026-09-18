package com.plugin.sdk.plugin;

import android.content.Context;
import android.view.View;

/**
 * 补丁入口类。
 * <p>
 * 宿主通过反射加载补丁 dex 后，反射调用本类的静态方法。
 * 本类只依赖 android.jar，不依赖任何宿主类，保证补丁可以独立编译。
 * <p>
 * 补丁资源用 aapt2 --shared-lib 编译，R 常量是 0x00 占位，需在加载资源后
 * 调用 {@code R.onResourcesLoaded(packageId)} 才会变成真实 id。
 */
public final class PluginEntry {

    private PluginEntry() {
    }

    /**
     * 补丁版本号，宿主可用来展示 / 对比是否需要更新。
     */
    public static String getVersion() {
        return "2.0.0";
    }

    /**
     * 初始化补丁资源：加载补丁 APK 资源并填充 R 常量。
     * 宿主在加载补丁 dex 后调用一次（幂等）。
     */
    public static void initResources(Context context, String patchPath) {
        PluginResources.init(context, patchPath);
    }

    /**
     * 宿主直接拿一个补丁里的 View 嵌入显示（演示「dex + 资源」整条链路）。
     */
    public static View createView(Context context) {
        return PluginResources.inflate(context, R.layout.plugin_activity);
    }
}
