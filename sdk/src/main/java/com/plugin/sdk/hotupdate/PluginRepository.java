package com.plugin.sdk.hotupdate;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 插件包管理：定位 / 导入 / 删除插件 APK。
 * <p>
 * 插件统一存放在 {@code filesDir/plugin/plugin_main.apk}，这是整个 SDK 的运行载体。
 * 骨架阶段提供两个来源：
 * <ul>
 *   <li>assets/plugin_main.apk —— 快速验收（插件与宿主一起打包，adb install 即可）</li>
 *   <li>/sdcard/Download/plugin_main.apk —— 仅 API &lt; 29 或已授予存储权限时可用，
 *       详见 {@link #importFromSdcard} 的说明</li>
 * </ul>
 * <p>
 * <b>导入必须走"临时文件 + rename"</b>，不能就地覆盖正式插件文件：
 * 当前进程可能已经通过 {@code DexClassLoader} 把 plugin_main.apk 映射进 ART 了，
 * 就地截断重写会让映射页读到越界数据，触发 SIGBUS（native 崩溃，Java 层拿不到堆栈）。
 * rename 只替换目录项，旧 inode 在被取消映射前始终有效，因此不会影响已映射的 dex。
 * 同时 rename 具备原子性，也避免了"导入到一半进程被杀、留下半截插件"的问题。
 */
public final class PluginRepository {

    private static final String PLUGIN_DIR = "plugin";
    private static final String PLUGIN_NAME = "plugin_main.apk";
    private static final String TEMP_NAME = "plugin_main.tmp";

    /** 仅 API &lt; 29 或已授予存储权限时可用。 */
    private static final String SDCARD_PATH = "/sdcard/Download/plugin_main.apk";

    private PluginRepository() {
    }

    /** 返回已存在的插件 APK 路径，不存在则返回 null。 */
    public static String findPlugin(Context context) {
        File file = pluginFile(context);
        return (file.isFile() && file.length() > 0) ? file.getAbsolutePath() : null;
    }

    /**
     * 从 /sdcard/Download/plugin_main.apk 导入到私有目录，返回导入后的路径。
     * <p>
     * 注意：targetSdk 30 起应用无法用文件路径读取 Download 目录下的非媒体文件，
     * 该方法在 Android 11+ 上会失败。真机验证建议改用 adb 把插件推到应用私有目录，
     * 或由接入方下载到自己的 filesDir / cacheDir 后调用 {@link #importFromFile}。
     */
    public static String importFromSdcard(Context context) throws IOException {
        File src = new File(SDCARD_PATH);
        if (!src.isFile()) {
            throw new IOException("未找到 " + SDCARD_PATH
                    + "（Android 11+ 应用无权按路径读取 Download 目录，请改用 importFromFile）");
        }
        return importFromFile(context, src);
    }

    /** 从任意可读文件导入插件（推荐：接入方先下载到私有目录再调用）。 */
    public static String importFromFile(Context context, File src) throws IOException {
        if (src == null || !src.isFile()) {
            throw new IOException("插件源文件不存在: " + src);
        }
        File tmp = tempFile(context);
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(tmp)) {
            copyStream(in, out);
        }
        return commit(context, tmp);
    }

    /** 从宿主 assets/plugin_main.apk 导入（快速验收用）。 */
    public static String importFromAssets(Context context) throws IOException {
        File tmp = tempFile(context);
        try (InputStream in = context.getAssets().open(PLUGIN_NAME);
             OutputStream out = new FileOutputStream(tmp)) {
            copyStream(in, out);
        }
        return commit(context, tmp);
    }

    /**
     * 删除插件。
     * <p>
     * 语义是"删除插件文件，重启后回到无插件状态"：当前进程里已经合并进宿主
     * ClassLoader 的插件 dex 无法撤销，本次运行仍会继续使用插件代码。
     */
    public static void clear(Context context) {
        File file = pluginFile(context);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        File tmp = tempFile(context);
        if (tmp.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /**
     * 把临时文件原子替换为正式插件文件。
     * <p>
     * 优先用 rename（Linux 上是原子替换，且不会影响已被 mmap 的旧文件）；
     * rename 失败时退化为复制，保证功能不中断。
     */
    private static String commit(Context context, File tmp) throws IOException {
        File dst = pluginFile(context);
        if (tmp.renameTo(dst)) {
            return dst.getAbsolutePath();
        }
        // 兜底：跨文件系统或目标被占用时 rename 可能失败
        copy(tmp, dst);
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        return dst.getAbsolutePath();
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
    }

    /** 取插件文件，目录创建失败时抛异常，避免调用方拿到 null。 */
    private static File pluginFile(Context context) {
        File dir = pluginDir(context);
        return new File(dir, PLUGIN_NAME);
    }

    private static File tempFile(Context context) {
        File dir = pluginDir(context);
        File tmp = new File(dir, TEMP_NAME);
        if (tmp.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        return tmp;
    }

    private static File pluginDir(Context context) {
        File dir = new File(context.getFilesDir(), PLUGIN_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            // 目录已存在但 mkdirs 返回 false 是允许的，这里再确认一次
            if (!dir.isDirectory()) {
                throw new IllegalStateException("创建插件目录失败: " + dir);
            }
        }
        return dir;
    }

    /** 保留给需要就地复制的场景（例如导入失败后的兜底）。 */
    private static void copy(File src, File dst) throws IOException {
        if (dst.exists() && !dst.canWrite()) {
            //noinspection ResultOfMethodCallIgnored
            dst.setWritable(true);
        }
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            copyStream(in, out);
        }
    }
}
