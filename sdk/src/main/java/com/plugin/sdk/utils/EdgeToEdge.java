package com.plugin.sdk.utils;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Edge-to-edge 全屏适配工具。
 * <p>
 * 行为对齐 AndroidX 的 {@code androidx.activity.EdgeToEdge}，但用纯 framework API 实现，
 * 不引入任何 AndroidX 依赖，保持 SDK 零依赖。
 * <p>
 * 与 {@code WindowCompat} / {@code WindowInsetsControllerCompat} 的对应关系：
 * <ul>
 *   <li>{@code setDecorFitsSystemWindows} —— API 30+ 用 {@link Window#setDecorFitsSystemWindows}，
 *       低版本改用 {@link View#setSystemUiVisibility} 的 LAYOUT_* 标志。</li>
 *   <li>状态栏 / 导航栏图标亮色 —— API 30+ 用 {@link WindowInsetsController}，
 *       低版本用 {@code SYSTEM_UI_FLAG_LIGHT_STATUS_BAR}（API 23+）与
 *       {@code SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR}（API 26+）。</li>
 * </ul>
 * <p>
 * 注意：targetSdk 35 起系统强制 edge-to-edge，{@code setStatusBarColor} /
 * {@code setNavigationBarColor} 在 Android 15+ 上不再生效，此处传入的 scrim 颜色会被忽略，
 * 官方 AndroidX 实现同样如此。
 */
public final class EdgeToEdge {

    private static final String TAG = "EdgeToEdge";

    /** 平台默认浅色蒙层：Color.argb(0xe6, 0xFF, 0xFF, 0xFF)。 */
    private static final int DEFAULT_LIGHT_SCRIM = 0xE6FFFFFF;

    /** 平台默认深色蒙层：Color.argb(0x80, 0x1b, 0x1b, 0x1b)。 */
    private static final int DEFAULT_DARK_SCRIM = 0x801B1B1B;

    /** 取值与 UiModeManager 的 MODE_NIGHT_* 一致，避免直接依赖已废弃常量。 */
    private static final int NIGHT_MODE_AUTO = 0;
    private static final int NIGHT_MODE_NO = 1;
    private static final int NIGHT_MODE_YES = 2;

    private EdgeToEdge() {
    }

    /**
     * 以默认样式开启 edge-to-edge：状态栏透明，导航栏使用平台默认蒙层颜色。
     * <p>
     * 建议在 Activity 的 {@code onCreate} 中、{@code super.onCreate()} 之后尽早调用。
     */
    public static void enable(Activity activity) {
        enable(activity,
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.auto(DEFAULT_LIGHT_SCRIM, DEFAULT_DARK_SCRIM));
    }

    /** 自定义状态栏和导航栏样式开启 edge-to-edge。 */
    public static void enable(Activity activity,
                              SystemBarStyle statusBarStyle,
                              SystemBarStyle navigationBarStyle) {
        if (activity == null || statusBarStyle == null || navigationBarStyle == null) {
            return;
        }
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        View decorView = window.getDecorView();
        Resources resources = decorView.getResources();

        int sdk = Build.VERSION.SDK_INT;
        try {
            // detectDarkMode 可能来自接入方自定义的 DarkModeDetector，放在 try 内，
            // 避免它抛异常时把异常带到 Activity.onCreate 上，把全屏适配变成启动崩溃源。
            boolean statusBarIsDark = statusBarStyle.detectDarkMode(resources);
            boolean navigationBarIsDark = navigationBarStyle.detectDarkMode(resources);
            if (sdk >= 29) {
                setUpApi29(window, decorView, statusBarStyle, navigationBarStyle,
                        statusBarIsDark, navigationBarIsDark);
            } else if (sdk >= 26) {
                setUpApi26(window, decorView, statusBarStyle, navigationBarStyle,
                        statusBarIsDark, navigationBarIsDark);
            } else if (sdk >= 23) {
                setUpApi23(window, decorView, statusBarStyle, navigationBarStyle,
                        statusBarIsDark, navigationBarIsDark);
            } else if (sdk >= 21) {
                setUpApi21(window);
            }
            // API 21 以下没有 edge-to-edge，不做任何事。
        } catch (Throwable t) {
            AppLogUtils.w(TAG, "开启 edge-to-edge 失败: " + t);
        }
    }

    /** API 21~22：只能靠半透明标志，没有蒙层样式可控。 */
    @SuppressWarnings("deprecation")
    private static void setUpApi21(Window window) {
        setDecorFitsSystemWindows(window, false);
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
    }

    /** API 23~25：状态栏可设颜色和亮色图标，导航栏沿用深色蒙层。 */
    @SuppressWarnings("deprecation")
    private static void setUpApi23(Window window, View decorView,
                                   SystemBarStyle statusBarStyle, SystemBarStyle navigationBarStyle,
                                   boolean statusBarIsDark, boolean navigationBarIsDark) {
        setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(statusBarStyle.getScrim(statusBarIsDark));
        window.setNavigationBarColor(navigationBarStyle.darkScrim);
        setAppearanceLightStatusBars(window, decorView, !statusBarIsDark);
    }

    /** API 26~28：导航栏也可以设置亮色图标。 */
    @SuppressWarnings("deprecation")
    private static void setUpApi26(Window window, View decorView,
                                   SystemBarStyle statusBarStyle, SystemBarStyle navigationBarStyle,
                                   boolean statusBarIsDark, boolean navigationBarIsDark) {
        setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(statusBarStyle.getScrim(statusBarIsDark));
        window.setNavigationBarColor(navigationBarStyle.getScrim(navigationBarIsDark));
        setAppearanceLightStatusBars(window, decorView, !statusBarIsDark);
        setAppearanceLightNavigationBars(window, decorView, !navigationBarIsDark);
    }

    /** API 29+：手势导航下系统自己保证对比度，可关闭强制蒙层。 */
    @SuppressWarnings("deprecation")
    private static void setUpApi29(Window window, View decorView,
                                   SystemBarStyle statusBarStyle, SystemBarStyle navigationBarStyle,
                                   boolean statusBarIsDark, boolean navigationBarIsDark) {
        setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(statusBarStyle.getScrimWithEnforcedContrast(statusBarIsDark));
        window.setNavigationBarColor(
                navigationBarStyle.getScrimWithEnforcedContrast(navigationBarIsDark));
        window.setStatusBarContrastEnforced(false);
        window.setNavigationBarContrastEnforced(navigationBarStyle.nightMode == NIGHT_MODE_AUTO);
        setAppearanceLightStatusBars(window, decorView, !statusBarIsDark);
        setAppearanceLightNavigationBars(window, decorView, !navigationBarIsDark);
    }

    /**
     * 等价于 {@code WindowCompat.setDecorFitsSystemWindows(window, fit)}。
     * API 30 起用官方方法，低版本回退到 {@link View#setSystemUiVisibility} 的布局标志。
     */
    @SuppressWarnings("deprecation")
    private static void setDecorFitsSystemWindows(Window window, boolean fit) {
        if (Build.VERSION.SDK_INT >= 30) {
            Api30.setDecorFitsSystemWindows(window, fit);
            return;
        }
        View decorView = window.getDecorView();
        int flags = decorView.getSystemUiVisibility();
        int layoutFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (fit) {
            flags &= ~layoutFlags;
        } else {
            flags |= layoutFlags;
        }
        decorView.setSystemUiVisibility(flags);
    }

    /**
     * 等价于 {@code WindowInsetsControllerCompat.isAppearanceLightStatusBars}。
     */
    @SuppressWarnings("deprecation")
    private static void setAppearanceLightStatusBars(Window window, View decorView, boolean isLight) {
        if (Build.VERSION.SDK_INT >= 30) {
            Api30.setLightStatusBars(window, isLight);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = decorView.getSystemUiVisibility();
            if (isLight) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decorView.setSystemUiVisibility(flags);
        }
    }

    /**
     * 等价于 {@code WindowInsetsControllerCompat.isAppearanceLightNavigationBars}。
     */
    @SuppressWarnings("deprecation")
    private static void setAppearanceLightNavigationBars(Window window, View decorView, boolean isLight) {
        if (Build.VERSION.SDK_INT >= 30) {
            Api30.setLightNavigationBars(window, isLight);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            int flags = decorView.getSystemUiVisibility();
            if (isLight) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decorView.setSystemUiVisibility(flags);
        }
    }

    /**
     * API 30+ 才存在的类型集中放在这个内部类里。
     * 内部类只会在 API 30+ 时被加载，低版本设备不会对它做类校验，因此不会出现
     * NoClassDefFoundError / VerifyError。
     */
    private static final class Api30 {

        private Api30() {
        }

        static void setDecorFitsSystemWindows(Window window, boolean fit) {
            window.setDecorFitsSystemWindows(fit);
        }

        static void setLightStatusBars(Window window, boolean isLight) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller == null) {
                return;
            }
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
            controller.setSystemBarsAppearance(isLight ? mask : 0, mask);
        }

        static void setLightNavigationBars(Window window, boolean isLight) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller == null) {
                return;
            }
            int mask = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(isLight ? mask : 0, mask);
        }
    }

    /** 深色模式探测器，便于接入方接入自己的夜间模式判断逻辑。 */
    public interface DarkModeDetector {
        boolean isDarkMode(Resources resources);
    }

    /** 状态栏 / 导航栏样式，对应 AndroidX 的 {@code SystemBarStyle}。 */
    public static final class SystemBarStyle {

        private final int lightScrim;
        private final int darkScrim;
        private final int nightMode;
        private final DarkModeDetector darkModeDetector;

        private SystemBarStyle(int lightScrim, int darkScrim, int nightMode,
                               DarkModeDetector darkModeDetector) {
            this.lightScrim = lightScrim;
            this.darkScrim = darkScrim;
            this.nightMode = nightMode;
            this.darkModeDetector = darkModeDetector;
        }

        boolean detectDarkMode(Resources resources) {
            return darkModeDetector != null && darkModeDetector.isDarkMode(resources);
        }

        /**
         * 跟随系统深色模式自动切换蒙层颜色。
         * <p>
         * API 29+ 手势导航下系统自行保证对比度，传入的颜色可能不被使用；
         * API 28 及以下则按深色模式在 lightScrim / darkScrim 之间选择。
         */
        public static SystemBarStyle auto(int lightScrim, int darkScrim) {
            return auto(lightScrim, darkScrim, new DarkModeDetector() {
                @Override
                public boolean isDarkMode(Resources resources) {
                    return (resources.getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                }
            });
        }

        /** 自定义深色模式判断逻辑的 {@link #auto(int, int)}。 */
        public static SystemBarStyle auto(int lightScrim, int darkScrim, DarkModeDetector detector) {
            return new SystemBarStyle(lightScrim, darkScrim, NIGHT_MODE_AUTO, detector);
        }

        /** 始终使用同一个蒙层颜色，用于深色图标场景。 */
        public static SystemBarStyle dark(int scrim) {
            return new SystemBarStyle(scrim, scrim, NIGHT_MODE_YES, new DarkModeDetector() {
                @Override
                public boolean isDarkMode(Resources resources) {
                    return true;
                }
            });
        }

        /** 始终使用浅色蒙层，深色系统图标场景下回退到 darkScrim。 */
        public static SystemBarStyle light(int scrim, int darkScrim) {
            return new SystemBarStyle(scrim, darkScrim, NIGHT_MODE_NO, new DarkModeDetector() {
                @Override
                public boolean isDarkMode(Resources resources) {
                    return false;
                }
            });
        }

        private int getScrim(boolean isDark) {
            return isDark ? darkScrim : lightScrim;
        }

        private int getScrimWithEnforcedContrast(boolean isDark) {
            if (nightMode == NIGHT_MODE_AUTO) {
                return Color.TRANSPARENT;
            }
            return isDark ? darkScrim : lightScrim;
        }
    }
}
