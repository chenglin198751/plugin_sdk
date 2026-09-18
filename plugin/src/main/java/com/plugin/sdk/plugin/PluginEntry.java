package com.plugin.sdk.plugin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

/**
 * 补丁入口类。
 * <p>
 * 宿主通过反射加载补丁 dex 后，反射调用本类的静态方法。
 * 本类只依赖 android.jar，不依赖任何宿主类，保证补丁可以独立编译。
 * <p>
 * R 常量由 aapt2 以 --package-id 0x80 生成，与宿主（0x7f）天然隔离，
 * 因此即使接入方 App 有大量 0x7f 资源，也不会与插件资源冲突。
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
     * 宿主直接拿一个补丁里的 View 嵌入显示（演示「dex + 资源」整条链路）。
     */
    public static View createView(Context context) {
        return LayoutInflater.from(context).inflate(R.layout.plugin_activity, null);
    }
}
