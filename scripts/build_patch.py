#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一键构建插件 APK（PluginSdk 热更新插件）。

核心目的：把插件资源编译成独立的 packageId=0x80（宿主是 0x7f），
这样即使接入方 App 有大量自己的 0x7f 资源，也不会与插件资源冲突。

流程：
  1. aapt2 compile   编译插件 res/ 资源
  2. aapt2 link      链接资源，--package-id 0x80 实现资源 id 隔离，
                     同时 --java 生成 0x80 开头的 R.java（插件代码引用它）
  3. javac           编译插件 Java 源码（依赖 android.jar + 生成的 R.java）
  4. d8              .class -> classes.dex
  5. zip             把 classes.dex 塞进 APK
  6. zipalign        4 字节对齐（必须在签名之前）
  7. apksigner       用 debug keystore 签名（V1+V2，与宿主一致）

用法（在项目根目录执行）：
    python scripts/build_patch.py

产物：
    plugin/build/patch.apk

前置：本机已安装 Android SDK（默认 D:/AndroidSdk/Sdk，可用 ANDROID_HOME 覆盖），
      且已存在 debug.keystore（跑过一次 gradle 构建即会自动生成）。
"""
import glob
import os
import shutil
import subprocess
import sys
import zipfile

# ----------------------------- 路径配置 -----------------------------
ANDROID_SDK = os.environ.get("ANDROID_HOME") or "D:/AndroidSdk/Sdk"
BUILD_TOOLS = os.path.join(ANDROID_SDK, "build-tools", "37.0.0")
ANDROID_JAR = os.path.join(ANDROID_SDK, "platforms", "android-37.0", "android.jar")

AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe")
D8_JAR = os.path.join(BUILD_TOOLS, "lib", "d8.jar")
APKSIGNER_JAR = os.path.join(BUILD_TOOLS, "lib", "apksigner.jar")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(SCRIPT_DIR)
PLUGIN_DIR = os.path.join(ROOT, "plugin")
PLUGIN_RES = os.path.join(PLUGIN_DIR, "src", "main", "res")
PLUGIN_SRC = os.path.join(PLUGIN_DIR, "src", "main", "java")
PLUGIN_MANIFEST = os.path.join(PLUGIN_DIR, "src", "main", "AndroidManifest.xml")

BUILD_DIR = os.path.join(PLUGIN_DIR, "build")
COMPILED_DIR = os.path.join(BUILD_DIR, "compiled")
GEN_DIR = os.path.join(BUILD_DIR, "gen")
CLASSES_DIR = os.path.join(BUILD_DIR, "classes")
DEX_DIR = os.path.join(BUILD_DIR, "dex")

# 签名：debug keystore（与宿主一致，验签「补丁签名==宿主签名」才能通过）
DEBUG_KEYSTORE = os.path.expanduser("~/.android/debug.keystore")
KEY_ALIAS = "androiddebugkey"
KEY_PASS = "android"

PACKAGE_ID = "0x80"          # 插件资源独立 packageId（共享库区间 0x80+），与宿主 0x7f 隔离
PACKAGE_NAME = "com.plugin.sdk.plugin"
OUTPUT_APK = os.path.join(BUILD_DIR, "patch.apk")

UNSIGNED_APK = os.path.join(BUILD_DIR, "unsigned.apk")
WITH_DEX_APK = os.path.join(BUILD_DIR, "with-dex.apk")
ALIGNED_APK = os.path.join(BUILD_DIR, "aligned.apk")


def run(cmd):
    print(">> " + " ".join(cmd))
    subprocess.check_call(cmd)


def clean():
    if os.path.isdir(BUILD_DIR):
        shutil.rmtree(BUILD_DIR)
    for d in (COMPILED_DIR, GEN_DIR, CLASSES_DIR, DEX_DIR):
        os.makedirs(d)


def compile_resources():
    print("\n[1/7] aapt2 compile 编译资源")
    run([AAPT2, "compile", "--dir", PLUGIN_RES, "-o", COMPILED_DIR])
    flat_files = sorted(glob.glob(os.path.join(COMPILED_DIR, "**", "*.flat"),
                                  recursive=True))
    if not flat_files:
        raise RuntimeError("未编译出任何 .flat 资源文件")
    return flat_files


def link_resources(flat_files):
    print("\n[2/7] aapt2 link 链接资源（packageId=%s）" % PACKAGE_ID)
    cmd = [AAPT2, "link", "-o", UNSIGNED_APK,
           "-I", ANDROID_JAR,
           "--manifest", PLUGIN_MANIFEST,
           "--package-id", PACKAGE_ID,
           "--custom-package", PACKAGE_NAME,
           "--java", GEN_DIR,
           "--min-sdk-version", "21",
           "--target-sdk-version", "37",
           "--auto-add-overlay"]
    for f in flat_files:
        cmd += ["-R", f]
    run(cmd)


def compile_java():
    print("\n[3/7] javac 编译插件源码")
    sources = sorted(glob.glob(os.path.join(PLUGIN_SRC, "**", "*.java"),
                               recursive=True))
    sources += sorted(glob.glob(os.path.join(GEN_DIR, "**", "*.java"),
                                recursive=True))
    cmd = ["javac",
           "-source", "8", "-target", "8",
           "-encoding", "UTF-8",
           "-cp", ANDROID_JAR,
           "-d", CLASSES_DIR]
    cmd += sources
    run(cmd)


def dex():
    print("\n[4/7] d8 生成 classes.dex")
    classes = sorted(glob.glob(os.path.join(CLASSES_DIR, "**", "*.class"),
                               recursive=True))
    if not classes:
        raise RuntimeError("未编译出任何 .class 文件")
    run(["java", "-cp", D8_JAR, "com.android.tools.r8.D8",
         "--lib", ANDROID_JAR,
         "--release",
         "--output", DEX_DIR] + classes)
    dex_file = os.path.join(DEX_DIR, "classes.dex")
    if not os.path.isfile(dex_file):
        raise RuntimeError("d8 未产出 classes.dex")
    return dex_file


def add_dex(dex_file):
    print("\n[5/7] 将 classes.dex 打入 APK")
    shutil.copyfile(UNSIGNED_APK, WITH_DEX_APK)
    with zipfile.ZipFile(WITH_DEX_APK, "a", zipfile.ZIP_DEFLATED) as zf:
        zf.write(dex_file, "classes.dex")


def align():
    print("\n[6/7] zipalign 对齐")
    run([ZIPALIGN, "-f", "4", WITH_DEX_APK, ALIGNED_APK])


def sign():
    print("\n[7/7] apksigner 用 debug keystore 签名（V1+V2，与宿主一致）")
    run(["java", "-jar", APKSIGNER_JAR, "sign",
         "--ks", DEBUG_KEYSTORE,
         "--ks-key-alias", KEY_ALIAS,
         "--ks-pass", "pass:" + KEY_PASS,
         "--key-pass", "pass:" + KEY_PASS,
         "--out", OUTPUT_APK,
         ALIGNED_APK])


def verify():
    print("\n[验证] apksigner verify")
    run(["java", "-jar", APKSIGNER_JAR, "verify", "--verbose", OUTPUT_APK])


def main():
    for path in (AAPT2, ANDROID_JAR, D8_JAR, APKSIGNER_JAR):
        if not os.path.exists(path):
            raise RuntimeError("缺少工具: %s" % path)
    if not os.path.exists(DEBUG_KEYSTORE):
        raise RuntimeError("缺少 debug.keystore: %s（先跑一次 ./gradlew 生成）" % DEBUG_KEYSTORE)

    clean()
    flat_files = compile_resources()
    link_resources(flat_files)
    compile_java()
    dex_file = dex()
    add_dex(dex_file)
    align()
    sign()
    verify()

    print("\n========================================")
    print("构建成功: %s" % OUTPUT_APK)
    print("资源 packageId: %s | 包名: %s" % (PACKAGE_ID, PACKAGE_NAME))
    print("签名方式: debug keystore（V1+V2，与宿主一致）")
    print("========================================")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as e:
        print("\n[失败] 命令退出码非 0，请检查上方输出。", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print("\n[失败] %s" % e, file=sys.stderr)
        sys.exit(1)
