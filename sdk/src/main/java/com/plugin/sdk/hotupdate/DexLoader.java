package com.plugin.sdk.hotupdate;

import android.content.Context;

import com.plugin.sdk.utils.AppLogUtils;

import java.io.File;
import java.io.IOException;

import dalvik.system.DexClassLoader;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

/**
 * dex 热更新：把补丁 APK 里的 classes.dex 合并进宿主 ClassLoader。
 * <p>
 * 原理：DexClassLoader 加载补丁 dex 后，反射拿到补丁与宿主的 dexElements，
 * 把补丁的 dexElements 排到数组最前面，再写回宿主的 ClassLoader。
 * 之后宿主进程内 new / Class.forName 都会优先命中补丁里的类。
 * <p>
 * 注意：targetSdk 34 及以上时，在 Android 14（API 34）及以上的设备上，动态代码加载
 * 要求 DEX/JAR/APK 文件必须 read-only，否则抛
 * {@code SecurityException: Writable dex file ... is not allowed}。
 * 因此在加载前必须对补丁文件 {@link File#setReadOnly()}。
 * <p>
 * 适用范围：类级别热更（新增类 / 替换「尚未被加载过」的类）。重启生效即可，
 * 不涉及对已在运行类的方法级替换（那需要 ART 层的 ArtMethod hack）。
 */
public final class DexLoader {

    private static final String TAG = "DexLoader";

    private DexLoader() {
    }

    public static void load(Context context, String patchApkPath) throws Exception {
        AppLogUtils.i(TAG, "开始加载补丁 dex: " + patchApkPath);
        Context app = context.getApplicationContext();
        ClassLoader hostLoader = app.getClassLoader();

        // Android 14+ 强制要求动态加载的代码文件 read-only，防止加载过程中被篡改
        File patchFile = new File(patchApkPath);
        if (!patchFile.setReadOnly()) {
            throw new IOException("Failed to make patch read-only: " + patchApkPath);
        }

        File codeCacheDir = app.getCodeCacheDir();
        if (codeCacheDir == null) {
            throw new IllegalStateException("codeCacheDir 为空");
        }
        File optimizedDir = new File(codeCacheDir, "patch_dex");
        if (!optimizedDir.exists() && !optimizedDir.mkdirs()) {
            throw new IllegalStateException("创建优化目录失败: " + optimizedDir);
        }

        DexClassLoader patchLoader = new DexClassLoader(
                patchApkPath,
                optimizedDir.getAbsolutePath(),
                null, // 补丁不带 native 库（骨架阶段）
                hostLoader);

        Object hostElements = getDexElements(hostLoader);
        Object patchElements = getDexElements(patchLoader);
        int patchCount = Array.getLength(patchElements);
        int hostCount = Array.getLength(hostElements);
        AppLogUtils.i(TAG, "dexElements 数量: 补丁=" + patchCount + ", 宿主=" + hostCount);
        if (patchCount == 0) {
            // DexPathList 对单个 dex 的 IOException 只记录到 dexElementsSuppressedExceptions
            // 后继续构造，不会抛出。不在这里拦截的话，会得到一个"合并成功但补丁类完全不存在"
            // 的假成功，真正的失败要等到反射调用 PluginEntry 时才以 ClassNotFoundException 出现。
            throw new IOException("补丁未贡献任何 dexElements，补丁可能损坏: " + patchApkPath);
        }
        Object merged = mergeArray(patchElements, hostElements);
        setDexElements(hostLoader, merged);
        AppLogUtils.i(TAG, "dexElements 合并完成，补丁元素已排到宿主前面");
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

    /** 把 patch 数组排在 host 数组前面，返回新的数组。 */
    private static Object mergeArray(Object patchElements, Object hostElements) {
        Class<?> componentType = hostElements.getClass().getComponentType();
        int patchLen = Array.getLength(patchElements);
        int hostLen = Array.getLength(hostElements);
        Object merged = Array.newInstance(componentType, patchLen + hostLen);
        System.arraycopy(patchElements, 0, merged, 0, patchLen);
        System.arraycopy(hostElements, 0, merged, patchLen, hostLen);
        return merged;
    }
}
