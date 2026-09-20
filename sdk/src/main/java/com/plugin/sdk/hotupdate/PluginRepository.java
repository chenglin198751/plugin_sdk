package com.plugin.sdk.hotupdate;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 插件包管理：定位 / 导入 / 删除插件 APK。
 * <p>
 * 插件按版本号平铺存放在 {@code filesDir/plugin/plugin_main_<versionCode>.apk}，
 * 这是整个 SDK 的运行载体。文件名带 versionCode，导入新版本<b>永不删除旧版本</b>，
 * 磁盘上的每个旧版本就是一个可回退点，加载失败时可以降级到次高版本。
 * <ul>
 *   <li>assets/plugin_main.apk —— 快速验收（插件与宿主一起打包，adb install 即可）</li>
 *   <li>/sdcard/Download/plugin_main.apk —— 仅 API &lt; 29 或已授予存储权限时可用</li>
 * </ul>
 * <p>
 * <b>导入必须走"临时文件 + rename"</b>，不能就地覆盖正式插件文件：
 * 当前进程可能已经通过 {@code DexClassLoader} 把某个版本的插件映射进 ART 了，
 * 就地截断重写会让映射页读到越界数据，触发 SIGBUS（native 崩溃，Java 层拿不到堆栈）。
 */
public final class PluginRepository {

    private static final String PLUGIN_DIR = "plugin";
    private static final String PLUGIN_NAME_PREFIX = "plugin_main_";
    private static final String PLUGIN_SUFFIX = ".apk";
    private static final String ASSETS_NAME = "plugin_main.apk";
    private static final String TEMP_NAME = "plugin_main.tmp";

    /** 仅 API &lt; 29 或已授予存储权限时可用。 */
    private static final String SDCARD_PATH = "/sdcard/Download/plugin_main.apk";

    private PluginRepository() {
    }

    /**
     * 返回已存在的插件 APK 路径列表，按 versionCode 从高到低排序。
     * 用于加载时降级：从最高版本开始尝试，失败降级到次高版本。
     */
    public static List<String> findPlugins(Context context) {
        File dir = pluginDir(context);
        File[] files = dir.listFiles();
        List<File> matched = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.length() > 0
                        && f.getName().startsWith(PLUGIN_NAME_PREFIX)
                        && f.getName().endsWith(PLUGIN_SUFFIX)) {
                    matched.add(f);
                }
            }
        }
        // 按 versionCode 降序：高版本在前，低版本在后
        Collections.sort(matched, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return parseVersionCode(b.getName()) - parseVersionCode(a.getName());
            }
        });
        List<String> paths = new ArrayList<>();
        for (File f : matched) {
            paths.add(f.getAbsolutePath());
        }
        return paths;
    }

    /**
     * 从 /sdcard/Download/plugin_main.apk 导入到私有目录，返回导入后的路径。
     * <p>
     * 注意：targetSdk 30 起应用无法用文件路径读取 Download 目录下的非媒体文件，
     * 该方法在 Android 11+ 上会失败，应改用 {@link #importFromFile}。
     */
    public static String importFromSdcard(Context context) throws IOException {
        File src = new File(SDCARD_PATH);
        if (!src.isFile()) {
            throw new IOException("未找到 " + SDCARD_PATH
                    + "（Android 11+ 应用无权按路径读取 Download 目录，请改用 importFromFile）");
        }
        return importFromFile(context, src);
    }

    /**
     * 从任意可读文件导入插件，返回导入后的路径。
     * <p>
     * 用 APK 元数据里的 versionCode 命名文件（{@code plugin_main_<versionCode>.apk}），
     * 这样文件名版本天然等于包内版本。旧版本文件不删除。
     */
    public static String importFromFile(Context context, File src) throws IOException {
        if (src == null || !src.isFile()) {
            throw new IOException("插件源文件不存在: " + src);
        }
        int versionCode = readVersionCode(context, src);
        if (versionCode <= 0) {
            throw new IOException("无法读取插件 versionCode: " + src);
        }
        File tmp = tempFile(context);
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(tmp)) {
            copyStream(in, out);
        }
        File dst = pluginFileFor(context, versionCode);
        commit(tmp, dst);
        return dst.getAbsolutePath();
    }

    /** 从宿主 assets/plugin_main.apk 导入（快速验收用），按包内 versionCode 命名。 */
    public static String importFromAssets(Context context) throws IOException {
        File tmp = tempFile(context);
        try (InputStream in = context.getAssets().open(ASSETS_NAME);
             OutputStream out = new FileOutputStream(tmp)) {
            copyStream(in, out);
        }
        int versionCode = readVersionCode(context, tmp);
        if (versionCode <= 0) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("无法读取 assets/" + ASSETS_NAME + " 的 versionCode");
        }
        File dst = pluginFileFor(context, versionCode);
        commit(tmp, dst);
        return dst.getAbsolutePath();
    }

    /**
     * 删除所有插件版本。
     * <p>
     * 语义是"删除插件文件，重启后回到无插件状态"：当前进程里已经合并进宿主
     * ClassLoader 的插件 dex 无法撤销，本次运行仍会继续使用插件代码。
     */
    public static void clear(Context context) {
        File dir = pluginDir(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }

    /**
     * 从文件名解析 versionCode，如 {@code plugin_main_100.apk -> 100}。
     * 解析失败（文件名不合规）返回 0。
     */
    public static int parseVersionCode(String fileName) {
        if (fileName == null
                || !fileName.startsWith(PLUGIN_NAME_PREFIX)
                || !fileName.endsWith(PLUGIN_SUFFIX)) {
            return 0;
        }
        String codeStr = fileName.substring(
                PLUGIN_NAME_PREFIX.length(), fileName.length() - PLUGIN_SUFFIX.length());
        try {
            return Integer.parseInt(codeStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 读 APK 元数据里的 versionCode（编译进 APK 的 AndroidManifest，不可篡改）。
     * 读不到返回 0。
     */
    public static int readVersionCode(Context context, File apk) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return (info == null) ? 0 : info.versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 把临时文件原子替换为目标插件文件。 */
    private static void commit(File tmp, File dst) throws IOException {
        if (tmp.renameTo(dst)) {
            return;
        }
        // 兜底：跨文件系统或目标被占用时 rename 可能失败
        copy(tmp, dst);
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
    }

    private static File pluginFileFor(Context context, int versionCode) {
        return new File(pluginDir(context), PLUGIN_NAME_PREFIX + versionCode + PLUGIN_SUFFIX);
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
            if (!dir.isDirectory()) {
                throw new IllegalStateException("创建插件目录失败: " + dir);
            }
        }
        return dir;
    }

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
