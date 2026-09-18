package com.plugin.sdk.plugin;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;

import java.lang.reflect.Method;

/**
 * 补丁侧资源自举工具。
 * <p>
 * 补丁 Activity 由宿主启动时，宿主的 Activity 尚未经历资源替换，
 * 因此这里不依赖宿主的全局 Resources，而是直接反射 addAssetPath 加载补丁
 * APK 自身资源，构造一个只含补丁资源的 Resources 用于 inflate 布局。
 * <p>
 * 这样补丁里的 XML 布局 / drawable / string / color 都能正常解析。
 */
public final class PluginResources {

    private PluginResources() {
    }

    /**
     * 用补丁 APK 自身的资源 inflate 一个布局。
     *
     * @param host       宿主上下文（用于拿到 displayMetrics / configuration）
     * @param patchApkPath 补丁 APK 的绝对路径
     * @param layoutId   补丁 R.layout 常量（packageId 0x80）
     */
    public static View inflate(Context host, String patchApkPath, int layoutId) {
        try {
            AssetManager am = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            addAssetPath.invoke(am, patchApkPath);

            Resources hostRes = host.getResources();
            final Resources patchRes = new Resources(
                    am,
                    hostRes.getDisplayMetrics(),
                    hostRes.getConfiguration());

            Context patchContext = new ContextWrapper(host) {
                @Override
                public Resources getResources() {
                    return patchRes;
                }

                @Override
                public AssetManager getAssets() {
                    return patchRes.getAssets();
                }
            };

            return LayoutInflater.from(host)
                    .cloneInContext(patchContext)
                    .inflate(layoutId, null);
        } catch (Throwable t) {
            throw new RuntimeException("inflate patch layout failed", t);
        }
    }
}
