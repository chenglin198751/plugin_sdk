package com.plugin.sdk.plugin;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import java.lang.reflect.Method;

/**
 * 补丁侧资源管理。
 * <p>
 * 插件 APK 用默认 0x7f 编译，加载时用 {@code addAssetPath}（普通加载），构造一个
 * 「只含插件资源的独立 Resources」，通过独立的 Context / Theme 隔离宿主资源
 * （宿主 0x7f 和插件 0x7f 分属两个独立 Resources 对象，互不冲突）。
 */
public final class PluginResources {

    private static final String TAG = "PluginResources";

    private static volatile Resources patchRes;
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
            Resources resources = new Resources(am, hostRes.getDisplayMetrics(),
                    hostRes.getConfiguration());

            patchRes = resources;
            Log.i(TAG, "插件资源验证: layout=" + resources.getResourceName(R.layout.plugin_activity)
                    + ", background=" + resources.getResourceName(R.drawable.plugin_bg)
                    + ", icon=" + resources.getResourceName(R.drawable.plugin_icon));
            inited = true;
        } catch (Throwable t) {
            Log.e(TAG, "初始化插件资源失败", t);
            throw new RuntimeException("init patch resources failed", t);
        }
    }

    /**
     * 用插件资源 inflate 一个布局。
     * <p>
     * Theme 在这里按「当前承载它的 Context」实时构造，不在 init 时缓存。原因：
     * <ul>
     *   <li>init 时传入的是 Application，只能拿到 application 的 theme；而宿主真正
     *       生效的主题通常声明在 Activity 上，缓存下来会导致插件控件样式与宿主不一致；</li>
     *   <li>宿主不同页面可能用不同主题，缓存单一 Theme 无法跟随。</li>
     * </ul>
     * 同时刷新一次 Configuration，让插件资源能跟随旋转、深色模式、语言切换等配置变更
     * （独立 Resources 不在 ResourcesManager 的托管列表里，框架不会自动更新它）。
     */
    public static View inflate(Context host, int layoutId) {
        Resources resources = patchRes;
        if (resources == null) {
            throw new IllegalStateException("PluginResources 未初始化");
        }
        Resources hostRes = host.getResources();
        try {
            //noinspection deprecation
            resources.updateConfiguration(hostRes.getConfiguration(), hostRes.getDisplayMetrics());
        } catch (Throwable t) {
            Log.w(TAG, "刷新插件资源配置失败，继续沿用旧配置: " + t);
        }

        // Theme 不在这里手动构造，交给 PluginContext（ContextThemeWrapper）懒加载：
        // 首次 getTheme() 时会用「插件 Resources」newTheme 并 setTo(host 的 theme)，
        // 既保证 Theme 绑定插件资源表，又跟随当前承载它的 Activity 主题。
        Context patchContext = new PluginContext(host, resources);
        Log.i(TAG, "inflate layout=" + resources.getResourceName(layoutId));
        return LayoutInflater.from(host)
                .cloneInContext(patchContext)
                .inflate(layoutId, null);
    }

    /**
     * 插件 XML 专用 Context（对齐 360 的 PluginContext）。
     * <p>
     * 继承 {@link ContextThemeWrapper} 而不是 {@link ContextWrapper}，原因是：
     * <ul>
     *   <li>重写 {@code getResources()} 后，首次 {@code getTheme()} 会用「插件 Resources」
     *       newTheme 并 setTo(base 的 theme)，让 Theme 绑定插件资源表——否则 View 构造时
     *       通过 obtainStyledAttributes 解析 background/src/textColor 会去宿主的资源表里
     *       查插件的资源 ID，抛 {@code Resources$NotFoundException}；</li>
     *   <li>{@code getSystemService(LAYOUT_INFLATER_SERVICE)} 会自动
     *       {@code cloneInContext(this)}，让整棵 View 树的 Context 锚定到插件；</li>
     *   <li>{@code getAssets()} 需要显式重写为插件 AssetManager（ContextThemeWrapper
     *       默认转发到 base 的 AssetManager，那是宿主的）。</li>
     * </ul>
     */
    private static final class PluginContext extends ContextThemeWrapper {

        private final Resources resources;

        PluginContext(Context base, Resources resources) {
            super(base, 0);
            this.resources = resources;
        }

        @Override
        public Resources getResources() {
            return resources;
        }

        @Override
        public AssetManager getAssets() {
            return resources.getAssets();
        }
    }
}
