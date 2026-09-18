package com.plugin.sdk.hotupdate;

import android.content.Context;

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
 * 注意：Android 14（API 34）起，动态代码加载要求 DEX/JAR/APK 文件必须 read-only，
 * 否则抛 {@code SecurityException: Writable dex file ... is not allowed}。
 * 因此在加载前必须对补丁文件 {@link File#setReadOnly()}。
 * <p>
 * 适用范围：类级别热更（新增类 / 替换「尚未被加载过」的类）。重启生效即可，
 * 不涉及对已在运行类的方法级替换（那需要 ART 层的 ArtMethod hack）。
 */
public final class DexLoader {

    private DexLoader() {
    }

    public static void load(Context context, String patchApkPath) throws Exception {
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
        Object merged = mergeArray(patchElements, hostElements);
        setDexElements(hostLoader, merged);
    }

    private static Object getDexElements(ClassLoader loader) throws Exception {
        Class<?> baseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
        Field pathListField = baseDexClassLoader.getDeclaredField("pathList");
        pathListField.setAccessible(true);
        Object pathList = pathListField.get(loader);

        Class<?> dexPathList = Class.forName("dalvik.system.DexPathList");
        Field dexElementsField = dexPathList.getDeclaredField("dexElements");
        dexElementsField.setAccessible(true);
        return dexElementsField.get(pathList);
    }

    private static void setDexElements(ClassLoader loader, Object dexElements) throws Exception {
        Class<?> baseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
        Field pathListField = baseDexClassLoader.getDeclaredField("pathList");
        pathListField.setAccessible(true);
        Object pathList = pathListField.get(loader);

        Class<?> dexPathList = Class.forName("dalvik.system.DexPathList");
        Field dexElementsField = dexPathList.getDeclaredField("dexElements");
        dexElementsField.setAccessible(true);
        dexElementsField.set(pathList, dexElements);
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
