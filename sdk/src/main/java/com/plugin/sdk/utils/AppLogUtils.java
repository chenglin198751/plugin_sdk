package com.plugin.sdk.utils;

import android.util.Log;

/**
 * 日志工具（迁移自 BaseMyProject 的 AppLogUtils）。
 * <p>
 * 保留原类的分段打印逻辑：Android 内核单条日志上限约 4K，超长自动分段。
 * 去掉了原类对 EnvToggle / ToggleSettings 的依赖，改用简单开关。
 */
public class AppLogUtils {

    /** 日志开关（默认开启，方便排查；接入方可在发布版关闭）。 */
    private static volatile boolean logEnable = true;

    private static final String TAG_PREFIX = "PluginSdk";

    /** 运行期开关日志输出。 */
    public static void setEnabled(boolean enabled) {
        logEnable = enabled;
    }

    public static void d(String tag, String msg) {
        if (logEnable) {
            print(TAG_PREFIX + "_" + tag, msg, Log.DEBUG);
        }
    }

    public static void e(String tag, String msg) {
        if (logEnable) {
            print(TAG_PREFIX + "_" + tag, msg, Log.ERROR);
        }
    }

    /** 带异常堆栈的 error 日志。 */
    public static void e(String tag, String msg, Throwable t) {
        if (logEnable) {
            print(TAG_PREFIX + "_" + tag, msg + "\n" + Log.getStackTraceString(t), Log.ERROR);
        }
    }

    public static void i(String tag, String msg) {
        if (logEnable) {
            print(TAG_PREFIX + "_" + tag, msg, Log.INFO);
        }
    }

    public static void v(String tag, String msg) {
        if (logEnable) {
            print(TAG_PREFIX + "_" + tag, msg, Log.VERBOSE);
        }
    }

    public static void w(String tag, String msg) {
        if (logEnable) {
            print(TAG_PREFIX + "_" + tag, msg, Log.WARN);
        }
    }

    /**
     * 单条日志分段大小。
     * <p>
     * Android 内核 {@code LOGGER_ENTRY_MAX_LEN} 是 4KB，且这个上限按<b>字节</b>计，
     * 不是字符数。中文在 UTF-8 下一个字符占 3 字节，按字符数判断会漏判
     * （4000 个中文字符约 12KB），导致日志被内核截断、丢掉尾部。
     * 这里留出 tag 和内核开销的余量，取 3000 字节。
     */
    private static final int SEGMENT_SIZE = 3000;
    private static final String SEG_START = "-----------log 长度超过了 " + SEGMENT_SIZE + " 字节，分段打印 start-----------";
    private static final String SEG_END = "-----------log 长度超过了 " + SEGMENT_SIZE + " 字节，分段打印 end-----------";

    private static void print(String tag, String msg, int level) {
        if (msg == null) {
            print2(tag, "null", level);
            return;
        }

        final int length = msg.length();
        if (utf8Length(msg) <= SEGMENT_SIZE) {
            print2(tag, msg, level);
            return;
        }

        print2(tag, SEG_START, level);
        int start = 0;
        while (start < length) {
            int end = start;
            int bytes = 0;
            while (end < length) {
                int charBytes = utf8Length(msg.charAt(end));
                if (bytes + charBytes > SEGMENT_SIZE) {
                    break;
                }
                bytes += charBytes;
                end++;
            }
            if (end == start) {
                // 兜底：单字符就超限时至少推进一个字符，避免死循环
                end = start + 1;
            } else if (end < length && Character.isHighSurrogate(msg.charAt(end - 1))) {
                // 不要把代理对从中间切开，否则会出现乱码
                end++;
            }
            print2(tag, msg.substring(start, end), level);
            start = end;
        }
        print2(tag, SEG_END, level);
    }

    /** 按 UTF-8 估算整串字节数。 */
    private static int utf8Length(String s) {
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            total += utf8Length(s.charAt(i));
        }
        return total;
    }

    /** 按 UTF-8 估算单个 char 的字节数（代理对两半各算 3 字节，略偏保守）。 */
    private static int utf8Length(char c) {
        if (c < 0x80) {
            return 1;
        }
        if (c < 0x800) {
            return 2;
        }
        return 3;
    }

    private static void print2(String tag, String log2, int level) {
        switch (level) {
            case Log.DEBUG:
                Log.d(tag, log2);
                break;
            case Log.ERROR:
                Log.e(tag, log2);
                break;
            case Log.INFO:
                Log.i(tag, log2);
                break;
            case Log.VERBOSE:
                Log.v(tag, log2);
                break;
            case Log.WARN:
                Log.w(tag, log2);
                break;
        }
    }
}
