package com.plugin.sdk.utils;

import android.util.Log;

/**
 * 日志工具（迁移自 BaseMyProject 的 AppLogUtils）。
 * <p>
 * 保留原类的分段打印逻辑：Android 内核单条日志上限约 4K，超长自动分段。
 * 去掉了原类对 EnvToggle / ToggleSettings 的依赖，改用简单开关。
 */
public class AppLogUtils {

    /** 日志总开关（骨架阶段默认开启，方便排查；发布前可关闭）。 */
    private static final boolean LOG_ENABLE = true;

    private static final String TAG_PREFIX = "PluginSdk";

    public static void d(String tag, String msg) {
        if (LOG_ENABLE) {
            print(TAG_PREFIX + "_" + tag, msg, Log.DEBUG);
        }
    }

    public static void e(String tag, String msg) {
        if (LOG_ENABLE) {
            print(TAG_PREFIX + "_" + tag, msg, Log.ERROR);
        }
    }

    /** 带异常堆栈的 error 日志。 */
    public static void e(String tag, String msg, Throwable t) {
        if (LOG_ENABLE) {
            print(TAG_PREFIX + "_" + tag, msg + "\n" + Log.getStackTraceString(t), Log.ERROR);
        }
    }

    public static void i(String tag, String msg) {
        if (LOG_ENABLE) {
            print(TAG_PREFIX + "_" + tag, msg, Log.INFO);
        }
    }

    public static void v(String tag, String msg) {
        if (LOG_ENABLE) {
            print(TAG_PREFIX + "_" + tag, msg, Log.VERBOSE);
        }
    }

    public static void w(String tag, String msg) {
        if (LOG_ENABLE) {
            print(TAG_PREFIX + "_" + tag, msg, Log.WARN);
        }
    }

    /**
     * Android 内核源码在 logger.h 中定义的最大字符长度 LOGGER_ENTRY_MAX_LEN 为 4*1024，
     * 如果日志超过 4K 就分段打印。
     */
    private static final int SEGMENT_SIZE = 4000;
    private static final String SEG_START = "-----------log 长度超过了 " + SEGMENT_SIZE + "，分段打印 start-----------";
    private static final String SEG_END = "-----------log 长度超过了 " + SEGMENT_SIZE + "，分段打印 end-----------";

    private static void print(String tag, String msg, int level) {
        final int length = msg.length();

        if (length <= SEGMENT_SIZE) {
            print2(tag, msg, level);
            return;
        }

        print2(tag, SEG_START, level);
        for (int i = 0; i < length; i += SEGMENT_SIZE) {
            print2(tag, msg.substring(i, Math.min(i + SEGMENT_SIZE, length)), level);
        }
        print2(tag, SEG_END, level);
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
