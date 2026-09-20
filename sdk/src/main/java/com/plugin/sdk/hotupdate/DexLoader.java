package com.plugin.sdk.hotupdate;

import android.content.Context;

import com.plugin.sdk.utils.AppLogUtils;

import java.io.File;
import java.io.IOException;

import dalvik.system.DexClassLoader;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

/**
 * dex 加载：把插件 APK 里的 classes.dex 合并进宿主 ClassLoader。
 * <p>
 * 原理：DexClassLoader 加载插件 dex 后，反射拿到插件与宿主的 dexElements，
 * 把插件的 dexElements 排到数组最前面，再写回宿主的 ClassLoader。
 * 之后宿主进程内 new / Class.forName 都会优先命中插件里的类。
 * <p>
 * 注意：targetSdk 34 及以上时，在 Android 14（API 34）及以上的设备上，动态代码加载
 * 要求 DEX/JAR/APK 文件必须 read-only，否则抛
 * {@code SecurityException: Writable dex file ... is not allowed}。
 * 因此在加载前必须对插件文件 {@link File#setReadOnly()}。
 * <p>
 * 适用范围：类级别热更（新增类 / 替换「尚未被加载过」的类）。重启生效即可，
 * 不涉及对已在运行类的方法级替换（那需要 ART 层的 ArtMethod hack）。
 */
public final class DexLoader {

    private static final String TAG = "DexLoader";

    private DexLoader() {
    }

    public static void load(Context context, String pluginApkPath) throws Exception {
        AppLogUtils.i(TAG, "开始加载插件 dex: " + pluginApkPath);
        Context app = context.getApplicationContext();
        ClassLoader hostLoader = app.getClassLoader();

        // Android 14+ 强制要求动态加载的代码文件 read-only，防止加载过程中被篡改
        File pluginFile = new File(pluginApkPath);
        if (!pluginFile.setReadOnly()) {
            throw new IOException("Failed to make plugin read-only: " + pluginApkPath);
        }

        File codeCacheDir = app.getCodeCacheDir();
        if (codeCacheDir == null) {
            throw new IllegalStateException("codeCacheDir 为空");
        }
        File optimizedDir = new File(codeCacheDir, "plugin_dex");
        if (!optimizedDir.exists() && !optimizedDir.mkdirs()) {
            throw new IllegalStateException("创建优化目录失败: " + optimizedDir);
        }

        DexClassLoader pluginLoader = new DexClassLoader(
                pluginApkPath,
                optimizedDir.getAbsolutePath(),
                null, // 插件不带 native 库（骨架阶段）
                hostLoader);

        Object hostElements = getDexElements(hostLoader);
        Object pluginElements = getDexElements(pluginLoader);
        int pluginCount = Array.getLength(pluginElements);
        int hostCount = Array.getLength(hostElements);
        AppLogUtils.i(TAG, "dexElements 数量: 插件=" + pluginCount + ", 宿主=" + hostCount);
        if (pluginCount == 0) {
            // DexPathList 对单个 dex 的 IOException 只记录到 dexElementsSuppressedExceptions
            // 后继续构造，不会抛出。不在这里拦截的话，会得到一个"合并成功但插件类完全不存在"
            // 的假成功，真正的失败要等到反射调用 PluginEntry 时才以 ClassNotFoundException 出现。
            throw new IOException("插件未贡献任何 dexElements，插件可能损坏: " + pluginApkPath);
        }
        Object merged = mergeArray(pluginElements, hostElements);
        setDexElements(hostLoader, merged);
        AppLogUtils.i(TAG, "dexElements 合并完成，插件元素已排到宿主前面");
    }

    private static Object getDexElements(ClassLoader loader) throws Exception {
        Object pathList = getPathList(loader);
        Class<?> dexPathList = Class.forName("dalvik.system.DexPathList");
        try {
            Field dexElementsField = dexPathList.getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            return dexElementsField.get(pathList);
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldException(
                    "DexPathList.dexElements 不存在，当前系统可能改了内部实现: " + e.getMessage());
        }
    }

    private static void setDexElements(ClassLoader loader, Object dexElements) throws Exception {
        Object pathList = getPathList(loader);
        Class<?> dexPathList = Class.forName("dalvik.system.DexPathList");
        try {
            Field dexElementsField = dexPathList.getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            dexElementsField.set(pathList, dexElements);
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldException(
                    "DexPathList.dexElements 不存在，当前系统可能改了内部实现: " + e.getMessage());
        }
    }

    private static Object getPathList(ClassLoader loader) throws Exception {
        Class<?> baseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
        try {
            Field pathListField = baseDexClassLoader.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            return pathListField.get(loader);
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldException(
                    "BaseDexClassLoader.pathList 不存在，当前系统可能改了内部实现: " + e.getMessage());
        }
    }

    /** 把 plugin 数组排在 host 数组前面，返回新的数组。 */
    private static Object mergeArray(Object pluginElements, Object hostElements) {
        Class<?> componentType = hostElements.getClass().getComponentType();
        int pluginLen = Array.getLength(pluginElements);
        int hostLen = Array.getLength(hostElements);
        Object merged = Array.newInstance(componentType, pluginLen + hostLen);
        System.arraycopy(pluginElements, 0, merged, 0, pluginLen);
        System.arraycopy(hostElements, 0, merged, pluginLen, hostLen);
        return merged;
    }
}
