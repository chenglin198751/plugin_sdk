package com.plugin.sdk.hotupdate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.plugin.sdk.PluginSdk;
import com.plugin.sdk.utils.AppLogUtils;

import java.lang.reflect.Method;

/**
 * 热更新引擎入口（单例）。
 * <p>
 * 在宿主 Application.onCreate 里调用 {@link #init(Context)}，在<b>后台线程</b>完成整条链路：
 * 找插件 -> 验签 -> 加载 dex -> 加载资源 -> 读版本。init 立即返回，不阻塞主线程。
 * <p>
 * 加载结果通过 {@link PluginSdk.LoadCallback} 回调（主线程）或 {@link #isPluginLoaded()} 查询。
 * <p>
 * 设计为「重启生效」：首次运行无插件，导入插件后下次启动自动生效。
 */
public final class HotUpdateEngine {

    private static final String TAG = "HotUpdateEngine";

    /** 插件入口类名，宿主通过反射调用，插件侧无需依赖 SDK。 */
    private static final String PLUGIN_ENTRY_CLASS = "com.plugin.sdk.plugin.PluginEntry";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static volatile HotUpdateEngine instance;

    private final Context appContext;
    private volatile boolean pluginLoaded = false;
    private volatile String pluginVersion = null;
    private volatile String pluginPath = null;
    private volatile String lastError = null;

    private HotUpdateEngine(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static void init(Context context) {
        init(context, null);
    }

    /**
     * 初始化热更新引擎，异步在后台线程完成加载。
     * <p>
     * 调用后立即返回；加载完成后通过 {@code callback}（主线程）通知，
     * 或通过 {@link #isPluginLoaded()} 查询。首次调用才真正发起加载，重复调用无副作用。
     */
    public static void init(Context context, PluginSdk.LoadCallback callback) {
        if (instance == null) {
            synchronized (HotUpdateEngine.class) {
                if (instance == null) {
                    instance = new HotUpdateEngine(context);
                    instance.startLoad(callback);
                }
            }
        }
    }

    public static HotUpdateEngine get() {
        return instance;
    }

    /** 在后台线程执行加载，完成后把结果切回主线程回调。 */
    private void startLoad(final PluginSdk.LoadCallback callback) {
        new Thread(() -> {
            load();
            if (callback != null) {
                mainHandler.post(() -> callback.onLoadFinished(pluginLoaded, pluginVersion, lastError));
            }
        }, "PluginSdk-Loader").start();
    }

    private void load() {
        AppLogUtils.i(TAG, "========== 开始加载插件 ==========");
        try {
            String path = PluginRepository.findPlugin(appContext);
            AppLogUtils.i(TAG, "findPlugin(filesDir) = " + path);
            if (path == null) {
                // 快速验收路径：若宿主 assets 里放了 plugin_main.apk，则首次启动自动导入
                try {
                    path = PluginRepository.importFromAssets(appContext);
                    AppLogUtils.i(TAG, "importFromAssets = " + path);
                } catch (java.io.IOException e) {
                    AppLogUtils.w(TAG, "importFromAssets 失败: " + e);
                }
            }
            if (path == null) {
                lastError = "未找到插件（首次运行或未导入）";
                AppLogUtils.w(TAG, lastError);
                return;
            }
            pluginPath = path;
            AppLogUtils.i(TAG, "插件路径 = " + path);

            // 1. 验签
            boolean verifyOk = PluginSignatureVerifier.verify(appContext, path);
            AppLogUtils.i(TAG, "验签结果 = " + verifyOk);
            if (!verifyOk) {
                lastError = "插件签名校验失败";
                AppLogUtils.e(TAG, lastError);
                return;
            }

            // 2. 加载 dex
            DexLoader.load(appContext, path);
            AppLogUtils.i(TAG, "dex 加载成功");

            // 3. 初始化插件资源
            initPluginResources(path);
            AppLogUtils.i(TAG, "插件资源初始化成功");

            pluginLoaded = true;
            pluginVersion = readPluginVersion();
            lastError = null;
            AppLogUtils.i(TAG, "========== 插件加载完成，版本 = " + pluginVersion + " ==========");
        } catch (Throwable t) {
            pluginLoaded = false;
            lastError = "加载失败: " + t;
            AppLogUtils.e(TAG, "加载插件异常", t);
        }
    }

    /** 初始化插件资源（反射调用插件的 PluginEntry.initResources）。 */
    private void initPluginResources(String path) throws Exception {
        Class<?> entry = Class.forName(PLUGIN_ENTRY_CLASS, true, appContext.getClassLoader());
        Method m = entry.getMethod("initResources", Context.class, String.class);
        m.invoke(null, appContext, path);
    }

    /** 插件 dex 加载后，反射读取插件版本号；读不到返回 null。 */
    private String readPluginVersion() {
        try {
            Class<?> entry = Class.forName(PLUGIN_ENTRY_CLASS, true, appContext.getClassLoader());
            Method m = entry.getMethod("getVersion");
            String v = (String) m.invoke(null);
            AppLogUtils.i(TAG, "readPluginVersion = " + v);
            return v;
        } catch (Throwable t) {
            AppLogUtils.w(TAG, "读取插件版本失败: " + t);
            return null;
        }
    }

    public boolean isPluginLoaded() {
        return pluginLoaded;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public String getPluginPath() {
        return pluginPath;
    }

    public String getLastError() {
        return lastError;
    }
}
