package com.plugin.sdk.hotupdate;

import android.content.Context;

import com.plugin.sdk.utils.AppLogUtils;

import java.lang.reflect.Method;

/**
 * 热更新引擎入口（单例）。
 * <p>
 * 在宿主 Application.onCreate 里调用 {@link #init(Context)}，完成整条链路：
 * 找补丁 -> 验签 -> 加载 dex -> 加载资源。
 * <p>
 * 设计为「重启生效」：首次运行无补丁，导入补丁后下次启动自动生效。
 */
public final class HotUpdateEngine {

    private static final String TAG = "HotUpdateEngine";

    private static volatile HotUpdateEngine instance;

    private final Context appContext;
    private volatile boolean patchLoaded = false;
    private volatile String patchVersion = null;
    private volatile String patchPath = null;
    private volatile String lastError = null;

    private HotUpdateEngine(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (HotUpdateEngine.class) {
                if (instance == null) {
                    instance = new HotUpdateEngine(context);
                    instance.load();
                }
            }
        }
    }

    public static HotUpdateEngine get() {
        return instance;
    }

    private void load() {
        AppLogUtils.i(TAG, "========== 开始加载补丁 ==========");
        try {
            String path = PatchRepository.findPatch(appContext);
            AppLogUtils.i(TAG, "findPatch(filesDir) = " + path);
            if (path == null) {
                // 快速验收路径：若宿主 assets 里放了 patch.apk，则首次启动自动导入
                try {
                    path = PatchRepository.importFromAssets(appContext);
                    AppLogUtils.i(TAG, "importFromAssets = " + path);
                } catch (java.io.IOException e) {
                    AppLogUtils.w(TAG, "importFromAssets 失败: " + e);
                }
            }
            if (path == null) {
                lastError = "未找到补丁（首次运行或未导入）";
                AppLogUtils.w(TAG, lastError);
                return;
            }
            patchPath = path;
            AppLogUtils.i(TAG, "补丁路径 = " + path);

            // 1. 验签
            boolean verifyOk = PatchSignatureVerifier.verify(appContext, path);
            AppLogUtils.i(TAG, "验签结果 = " + verifyOk);
            if (!verifyOk) {
                lastError = "补丁签名校验失败";
                AppLogUtils.e(TAG, lastError);
                return;
            }

            // 2. 加载 dex
            DexLoader.load(appContext, path);
            AppLogUtils.i(TAG, "dex 加载成功");

            // 3. 加载资源
            ResourceLoader.load(appContext, path);
            AppLogUtils.i(TAG, "资源加载成功");

            patchLoaded = true;
            patchVersion = readPatchVersion();
            lastError = null;
            AppLogUtils.i(TAG, "========== 补丁加载完成，版本 = " + patchVersion + " ==========");
        } catch (Throwable t) {
            patchLoaded = false;
            lastError = "加载失败: " + t;
            AppLogUtils.e(TAG, "加载补丁异常", t);
        }
    }

    /** 补丁 dex 加载后，反射读取补丁版本号。 */
    private String readPatchVersion() {
        try {
            Class<?> entry = Class.forName("com.plugin.sdk.plugin.PluginEntry");
            Method m = entry.getMethod("getVersion");
            String v = (String) m.invoke(null);
            AppLogUtils.i(TAG, "readPatchVersion = " + v);
            return v;
        } catch (Throwable t) {
            AppLogUtils.w(TAG, "读取补丁版本失败: " + t);
            return "未知";
        }
    }

    public boolean isPatchLoaded() {
        return patchLoaded;
    }

    public String getPatchVersion() {
        return patchVersion;
    }

    public String getPatchPath() {
        return patchPath;
    }

    public String getLastError() {
        return lastError;
    }
}
