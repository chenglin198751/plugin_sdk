# 插件模块的混淆配置。
#
# 当前 build.gradle 里 minifyEnabled = false，本文件暂时不生效。
# 一旦开启混淆，下面这些规则是必需的：宿主与插件之间没有共享的 Java 类型，
# 整个契约靠「类名 + 方法名 + 参数签名」反射维系（见 sdk 模块的
# ApkPluggingActivityProxy 与 HotUpdateEngine）。任何名字被 R8 重命名或裁剪，
# 宿主都会直接找不到入口，且失败发生在运行时。

# 宿主反射加载的入口类，类名会被写死成字符串
-keep class com.plugin.sdk.plugin.PluginEntry { *; }

# 宿主反射加载的插件侧生命周期总代理，类名会被写死成字符串
-keep class com.plugin.sdk.plugin.ApkProxyActivity { *; }

# 生命周期契约：宿主对 ApkProxyActivity 逐个 getMethod 查找这 18 个方法
-keep class com.plugin.sdk.plugin.ApkInterfaceForProxyActivity { *; }
-keepclassmembers class * implements com.plugin.sdk.plugin.ApkInterfaceForProxyActivity {
    public *;
}

# 插件页面按 viewId 被 new 出来，需要保留类和 public 无参构造函数
-keep class * extends com.plugin.sdk.plugin.PluginBaseActivity {
    public <init>();
}

# 资源被反射访问的部分（如 R 常量在 inflate 时按数值使用）无需额外规则，
# 但保留资源类本身可避免极端情况下的裁剪
-keep class com.plugin.sdk.plugin.R { *; }
-keep class com.plugin.sdk.plugin.R$* { *; }
