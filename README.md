# PluginSdk

Android 插件化热更新 SDK。对接入方只接入一次 AAR，后续通过下载插件 APK 更新 SDK 的代码和 UI。

不使用第三方插件化框架，核心机制自研。

## 模块结构

| 模块 | 类型 | 说明 |
|---|---|---|
| `sdk` | Android Library | 对外交付的 AAR，包含热更新引擎和宿主运行时 |
| `demo` | Android Application | 模拟第三方接入方的测试 App，用于验证接入流程 |
| `plugin` | Android Application | 被热更新的插件，编译为独立 APK |

## 构建与运行

```bash
./gradlew :demo:assembleDebug
```

这一条命令会自动完成：

```text
编译 plugin（:plugin:assembleRelease）
        ↓
复制 plugin-release.apk → demo/src/main/assets/plugin_main.apk
        ↓
编译 demo
```

安装验证：

```bash
adb install -r demo/build/outputs/apk/debug/demo-debug.apk
```

## 单独构建

```bash
./gradlew :plugin:assembleRelease   # 插件 APK
./gradlew :sdk:assembleDebug        # SDK AAR
```

产物路径：

```text
plugin/build/outputs/apk/release/plugin-release.apk
sdk/build/outputs/aar/sdk-debug.aar
```

## 工具链

```text
Android Gradle Plugin 9.4.0
Gradle 9.7.1
compileSdk 37 / targetSdk 37 / minSdk 21
Java 17
```

## 完整方案

实现原理、接入方式、技术细节和当前限制见 [热更新方案.md](热更新方案.md)。
