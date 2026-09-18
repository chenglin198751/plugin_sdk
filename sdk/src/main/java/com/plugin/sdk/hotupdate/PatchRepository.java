package com.plugin.sdk.hotupdate;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 补丁包管理：定位 / 导入 / 删除补丁 APK。
 * <p>
 * 补丁统一存放在 {@code filesDir/patch/patch.apk}。
 * 骨架阶段提供两个来源：
 * <ul>
 *   <li>assets/patch.apk —— 快速验收（补丁与宿主一起打包，adb install 即可）</li>
 *   <li>/sdcard/Download/patch.apk —— 真热更（运行时导入，重启生效）</li>
 * </ul>
 */
public final class PatchRepository {

    private static final String PATCH_DIR = "patch";
    private static final String PATCH_NAME = "patch.apk";
    private static final String SDCARD_PATH = "/sdcard/Download/patch.apk";

    private PatchRepository() {
    }

    /** 返回已存在的补丁 APK 路径，不存在则返回 null。 */
    public static String findPatch(Context context) {
        File file = patchFile(context);
        return (file != null && file.isFile() && file.length() > 0)
                ? file.getAbsolutePath() : null;
    }

    /** 从 /sdcard/Download/patch.apk 导入到私有目录，返回导入后的路径。 */
    public static String importFromSdcard(Context context) throws IOException {
        File src = new File(SDCARD_PATH);
        if (!src.isFile()) {
            throw new IOException("未找到 " + SDCARD_PATH + "，请先 adb push 补丁到该路径");
        }
        File dst = patchFile(context);
        copy(src, dst);
        return dst.getAbsolutePath();
    }

    /** 从宿主 assets/patch.apk 导入（快速验收用）。 */
    public static String importFromAssets(Context context) throws IOException {
        File dst = patchFile(context);
        ensureWritable(dst);
        try (InputStream in = context.getAssets().open(PATCH_NAME);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        return dst.getAbsolutePath();
    }

    /** 删除补丁（回滚到无补丁状态）。 */
    public static void clear(Context context) {
        File file = patchFile(context);
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static File patchFile(Context context) {
        File dir = new File(context.getFilesDir(), PATCH_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return new File(dir, PATCH_NAME);
    }

    /** 补丁被 setReadOnly 后，覆盖前需先恢复可写。 */
    private static void ensureWritable(File f) {
        if (f.exists() && !f.canWrite()) {
            //noinspection ResultOfMethodCallIgnored
            f.setWritable(true);
        }
    }

    private static void copy(File src, File dst) throws IOException {
        ensureWritable(dst);
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }
}
