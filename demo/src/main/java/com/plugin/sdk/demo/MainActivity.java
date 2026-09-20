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
 *   <li>插件状态展示（是否加载 / 版本 / 错误原因）</li>
 *   <li>从 sdcard 导入插件（真热更路径）</li>
 *   <li>加载插件 View（验证 dex + 资源加载链路）</li>
 *   <li>启动插件 Activity（验证插件独立 Activity）</li>
 * </ul>
 */
public class MainActivity extends Activity {

    private static final String PLUGIN_ENTRY = "com.plugin.sdk.plugin.PluginEntry";

    private TextView statusText;
    private FrameLayout pluginContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.tv_plugin_status);
        pluginContainer = findViewById(R.id.container_plugin);

        // 按钮 1：从 sdcard 导入插件（真热更路径，导入后重启 App 生效）
        Button importButton = findViewById(R.id.btn_import_plugin);
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importPlugin();
            }
        });

        // 按钮 2：加载插件 View（验证 dex + 资源加载链路，把插件根 View 嵌入宿主页面）
        Button loadViewButton = findViewById(R.id.btn_load_plugin_view);
        loadViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadPluginView();
            }
        });

        // 按钮 3：启动插件 Activity（验证插件独立 Activity，通过宿主占位 Activity 代理启动）
        Button startActivityButton = findViewById(R.id.btn_start_plugin_activity);
        startActivityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPluginActivity();
            }
        });

        refreshStatus();

        // 插件异步加载（后台线程），启动后稍作延时再刷新一次，加载完成即显示状态。
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshStatus();
            }
        }, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 插件异步加载，回到前台时刷新一次状态（加载可能刚完成）。
        refreshStatus();
    }

    private void refreshStatus() {
        String status;
        if (PluginSdk.isPluginLoaded()) {
            String version = PluginSdk.getPluginVersion();
            status = "插件已加载\n版本: " + (version == null ? "未知" : version)
                    + "\n路径: " + PluginSdk.getPluginPath();
        } else {
            String error = PluginSdk.getLastError();
            status = (error == null) ? "插件加载中…" : "插件加载失败\n原因: " + error;
        }
        statusText.setText(status);
    }

    private void importPlugin() {
        try {
            String path = PluginSdk.importPlugin(this);
            Toast.makeText(this, "导入成功，重启 App 生效\n" + path, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadPluginView() {
        if (!PluginSdk.isPluginLoaded()) {
            Toast.makeText(this, "插件未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Class<?> entry = Class.forName(PLUGIN_ENTRY);
            Method createView = entry.getMethod("createView", Context.class);
            // 传 Activity 而不是 Application：插件 View 的主题取自这个 Context，
            // 用 Application 会拿到 application 的主题而不是当前页面的主题。
            View view = (View) createView.invoke(null, this);
            pluginContainer.removeAllViews();
            pluginContainer.addView(view);
        } catch (Throwable t) {
            AppLogUtils.e("MainActivity", "加载插件 View 失败", t);
            Toast.makeText(this, "加载插件 View 失败: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private void startPluginActivity() {
        try {
            PluginSdk.startPluginActivity(this);
        } catch (Throwable t) {
            AppLogUtils.e("MainActivity", "启动插件 Activity 失败", t);
            Toast.makeText(this, "启动插件 Activity 失败: " + t, Toast.LENGTH_LONG).show();
        }
    }
}
