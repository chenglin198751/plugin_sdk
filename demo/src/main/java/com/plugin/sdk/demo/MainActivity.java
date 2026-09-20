package com.plugin.sdk.demo;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.plugin.sdk.PluginSdk;
import com.plugin.sdk.utils.AppLogUtils;

import java.lang.reflect.Method;

/**
 * demo 入口 Activity：模拟「接入方」使用 PluginSdk 对外 API 的方式。
 * <p>
 * 界面来自宿主自己的 XML 布局 {@code res/layout/activity_main.xml}，宿主拥有自己的
 * 0x7f 资源，与插件资源分属两个独立 Resources 对象。包含：
 * <ul>
 *   <li>补丁状态展示（是否加载 / 版本 / 错误原因）</li>
 *   <li>从 sdcard 导入补丁（真热更路径）</li>
 *   <li>加载补丁 View（验证 dex + 资源加载链路）</li>
 *   <li>启动补丁 Activity（验证补丁独立 Activity）</li>
 * </ul>
 */
public class MainActivity extends Activity {

    private static final String PLUGIN_ENTRY = "com.plugin.sdk.plugin.PluginEntry";

    private TextView statusText;
    private FrameLayout patchContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.tv_patch_status);
        patchContainer = findViewById(R.id.container_patch);

        Button importButton = findViewById(R.id.btn_import_patch);
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importPatch();
            }
        });

        Button loadViewButton = findViewById(R.id.btn_load_patch_view);
        loadViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadPatchView();
            }
        });

        Button startActivityButton = findViewById(R.id.btn_start_patch_activity);
        startActivityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPatchActivity();
            }
        });

        refreshStatus();
    }

    private void refreshStatus() {
        String status;
        if (PluginSdk.isPatchLoaded()) {
            String version = PluginSdk.getPatchVersion();
            status = "补丁已加载\n版本: " + (version == null ? "未知" : version)
                    + "\n路径: " + PluginSdk.getPatchPath();
        } else {
            String error = PluginSdk.getLastError();
            status = "补丁未加载\n原因: " + (error == null ? "无" : error);
        }
        statusText.setText(status);
    }

    private void importPatch() {
        try {
            String path = PluginSdk.importPatch(this);
            Toast.makeText(this, "导入成功，重启 App 生效\n" + path, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadPatchView() {
        if (!PluginSdk.isPatchLoaded()) {
            Toast.makeText(this, "补丁未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Class<?> entry = Class.forName(PLUGIN_ENTRY);
            Method createView = entry.getMethod("createView", Context.class);
            // 传 Activity 而不是 Application：插件 View 的主题取自这个 Context，
            // 用 Application 会拿到 application 的主题而不是当前页面的主题。
            View view = (View) createView.invoke(null, this);
            patchContainer.removeAllViews();
            patchContainer.addView(view);
        } catch (Throwable t) {
            AppLogUtils.e("MainActivity", "加载补丁 View 失败", t);
            Toast.makeText(this, "加载补丁 View 失败: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private void startPatchActivity() {
        try {
            PluginSdk.startPluginActivity(this);
        } catch (Throwable t) {
            AppLogUtils.e("MainActivity", "启动补丁 Activity 失败", t);
            Toast.makeText(this, "启动补丁 Activity 失败: " + t, Toast.LENGTH_LONG).show();
        }
    }
}
