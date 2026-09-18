package com.plugin.sdk.plugin;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * 补丁里的完整 Activity。
 * <p>
 * 由宿主通过 manifest 预注册的类名启动（宿主 dex 里并没有这个类，
 * 补丁 dex 合并进宿主 ClassLoader 后即可被加载）。
 * <p>
 * 资源采用「补丁自举」方式：不依赖宿主全局 Resources，而是从 Intent 里
 * 拿到补丁 APK 路径，用 {@link PluginResources} 加载补丁自身资源 inflate 布局。
 */
public class PluginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        String patchPath = getIntent().getStringExtra("patch_path");
        if (patchPath == null) {
            // 兜底：宿主可能没传，尝试用宿主 classloader 的 dex 信息解析（骨架阶段忽略）
            finish();
            return;
        }

        View view = PluginResources.inflate(this, patchPath, R.layout.plugin_activity);
        setContentView(view);

        TextView version = findViewById(R.id.tv_plugin_version);
        if (version != null) {
            version.setText("补丁版本 " + PluginEntry.getVersion());
        }
    }
}
