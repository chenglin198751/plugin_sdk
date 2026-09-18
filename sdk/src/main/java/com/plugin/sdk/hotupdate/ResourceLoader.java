package com.plugin.sdk.hotupdate;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 资源热更新：把补丁 APK 的资源合并进宿主进程。
 * <p>
 * 做法：新建一个 AssetManager，同时 addAssetPath 宿主 APK 与补丁 APK，
 * 然后用这个 AssetManager 替换进程内 Resources 的底层 mAssets。
 * 宿主资源 packageId=0x7f，补丁资源 packageId=0x80，两者天然隔离。
 * <p>
 * 覆盖范围：
 * <ul>
 *   <li>Application 的 Resources</li>
 *   <li>ResourcesManager 已缓存的 Resources（即当前进程内已创建的 Activity/Context 的资源）</li>
 * </ul>
 * 之后新创建的 Activity 如需拿到补丁资源，应由补丁侧用 {@code PluginResources}
 * 自举加载（见补丁模块），避免依赖本类对「后续新建 Activity」的完整 hook。
 */
public final class ResourceLoader {

    private ResourceLoader() {
    }

    public static void load(Context context, String patchApkPath) throws Exception {
        AssetManager merged = createMergedAssetManager(context, patchApkPath);
        replaceApplicationResources(context, merged);
        hookResourcesManager(merged);
    }

    private static AssetManager createMergedAssetManager(Context context, String patchApkPath)
            throws Exception {
        AssetManager am = AssetManager.class.newInstance();
        Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
        addAssetPath.setAccessible(true);
        // 必须同时加入宿主 APK，否则宿主自己的资源会丢失
        addAssetPath.invoke(am, context.getApplicationInfo().sourceDir);
        addAssetPath.invoke(am, patchApkPath);
        return am;
    }

    private static void replaceApplicationResources(Context context, AssetManager merged)
            throws Exception {
        Resources appRes = context.getApplicationContext().getResources();
        replaceAssetManager(appRes, merged);
    }

    private static void hookResourcesManager(AssetManager merged) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThread = atClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThread.setAccessible(true);
            Object at = sCurrentActivityThread.get(null);
            if (at == null) {
                return;
            }
            Field rmField = atClass.getDeclaredField("mResourcesManager");
            rmField.setAccessible(true);
            Object rm = rmField.get(at);
            if (rm == null) {
                return;
            }
            Class<?> rmClass = Class.forName("android.app.ResourcesManager");
            Field activeField = rmClass.getDeclaredField("mActiveResources");
            activeField.setAccessible(true);
            Object activeResources = activeField.get(rm);
            if (activeResources instanceof Map) {
                for (Object ref : ((Map<?, ?>) activeResources).values()) {
                    if (ref instanceof WeakReference) {
                        Resources res = (Resources) ((WeakReference<?>) ref).get();
                        if (res != null) {
                            replaceAssetManager(res, merged);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // 忽略：hook 失败不影响 dex 链路，补丁 Activity 走自举资源
        }
    }

    private static void replaceAssetManager(Resources res, AssetManager merged) throws Exception {
        Field implField = Resources.class.getDeclaredField("mResourcesImpl");
        implField.setAccessible(true);
        Object impl = implField.get(res);

        Class<?> implClass = Class.forName("android.content.res.ResourcesImpl");
        Field assetsField = implClass.getDeclaredField("mAssets");
        assetsField.setAccessible(true);
        assetsField.set(impl, merged);
    }
}
