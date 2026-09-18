package com.plugin.sdk.demo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.plugin.sdk.PluginSdk;
import com.plugin.sdk.utils.AppLogUtils;

import java.lang.reflect.Method;

/**
 * demo 入口 Activity：模拟「接入方」使用 PluginSdk 对外 API 的方式。
 * <p>
 * 用代码构建 UI（不依赖 XML 资源），包含：
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
        setContentView(buildUi());
        refreshStatus();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(px(24), px(48), px(24), px(24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Demo App（PluginSdk 接入方）");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(Color.DKGRAY);
        statusText.setPadding(0, px(16), 0, px(16));
        root.addView(statusText);

        root.addView(button("从 sdcard 导入补丁", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importPatch();
            }
        }));
        root.addView(button("加载补丁 View（dex+资源）", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadPatchView();
            }
        }));
        root.addView(button("启动补丁 Activity", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPatchActivity();
            }
        }));

        patchContainer = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = px(16);
        root.addView(patchContainer, lp);
        return root;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(8);
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshStatus() {
        String status;
        if (PluginSdk.isPatchLoaded()) {
            status = "补丁已加载\n版本: " + PluginSdk.getPatchVersion()
                    + "\n路径: " + PluginSdk.getPatchPath();
        } else {
            status = "补丁未加载\n原因: " + PluginSdk.getLastError();
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
            View view = (View) createView.invoke(null, getApplicationContext());
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

    private int px(int dp) {
        return (int) (getResources().getDisplayMetrics().density * dp + 0.5f);
    }
}
