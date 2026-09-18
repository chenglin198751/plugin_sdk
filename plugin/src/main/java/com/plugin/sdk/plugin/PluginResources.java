package com.plugin.sdk.plugin;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import java.lang.reflect.Method;

/**
 * 补丁侧资源管理（对齐 360 插件方案）。
 * <p>
 * 插件 APK 用默认 0x7f 编译，加载时用 addAssetPath（普通加载），
 * 构造一个「只含插件资源的独立 Resources」，通过 ContextWrapper 重写 getResources
 * 来隔离宿主资源（宿主 0x7f 和插件 0x7f 分属两个独立 Resources 对象，互不冲突）。
 */
public final class PluginResources {

    private static final String TAG = "PluginResources";

    private static volatile Resources patchRes;
    private static volatile Resources.Theme patchTheme;
    private static volatile boolean inited = false;

    private PluginResources() {
    }

    /** 初始化插件资源：addAssetPath 加载插件 APK，构造独立 Resources。幂等。 */
    public static synchronized void init(Context host, String patchApkPath) {
        if (inited) {
            return;
        }
        try {
            AssetManager am = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = ((Number) addAssetPath.invoke(am, patchApkPath)).intValue();
            if (cookie == 0) {
                throw new IllegalStateException("addAssetPath 返回 0: " + patchApkPath);
            }
            Log.i(TAG, "addAssetPath 成功, cookie=" + cookie + ", path=" + patchApkPath);

            Resources hostRes = host.getResources();
            Resources resources = new Resources(am, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
            Resources.Theme theme = resources.newTheme();
            Resources.Theme hostTheme = host.getTheme();
            if (hostTheme != null) {
                // 复制宿主的系统主题属性，保证 LinearLayout/TextView 等系统 View 保持宿主样式。
                // Theme 对象属于插件 Resources，因此 obtainStyledAttributes 返回的 TypedArray
                // 会使用插件资源表解析 0x7f 资源，而不是回到宿主 Resources。
                theme.setTo(hostTheme);
            }

            patchRes = resources;
            patchTheme = theme;
            Log.i(TAG, "插件资源验证: layout=" + resources.getResourceName(R.layout.plugin_activity)
                    + ", background=" + resources.getResourceName(R.drawable.plugin_bg)
                    + ", icon=" + resources.getResourceName(R.drawable.plugin_icon));
            inited = true;
        } catch (Throwable t) {
            Log.e(TAG, "初始化插件资源失败", t);
            throw new RuntimeException("init patch resources failed", t);
        }
    }

    /** 用插件资源 inflate 一个布局。 */
    public static View inflate(Context host, int layoutId) {
        Resources resources = patchRes;
        Resources.Theme theme = patchTheme;
        if (resources == null || theme == null) {
            throw new IllegalStateException("PluginResources 未初始化");
        }
        Context patchContext = new PluginContext(host, resources, theme);
        Log.i(TAG, "inflate layout=" + resources.getResourceName(layoutId));
        return LayoutInflater.from(host)
                .cloneInContext(patchContext)
                .inflate(layoutId, null);
    }

    /**
     * 插件 XML 专用 Context。
     *
     * 仅替换 getResources()/getAssets() 还不够：View 构造时会通过 getTheme()
     * 调用 obtainStyledAttributes。这里必须返回绑定插件 Resources 的 Theme，
     * 否则宿主 Theme 会尝试解析插件的 0x7f 资源 ID。
     */
    private static final class PluginContext extends ContextWrapper {

        private final Resources resources;
        private final Resources.Theme theme;

        PluginContext(Context base, Resources resources, Resources.Theme theme) {
            super(base);
            this.resources = resources;
            this.theme = theme;
        }

        @Override
        public Resources getResources() {
            return resources;
        }

        @Override
        public AssetManager getAssets() {
            return resources.getAssets();
        }

        @Override
        public Resources.Theme getTheme() {
            return theme;
        }

        @Override
        public void setTheme(int resid) {
            theme.applyStyle(resid, true);
        }
    }
}
