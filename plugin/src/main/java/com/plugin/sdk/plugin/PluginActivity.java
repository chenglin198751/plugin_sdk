package com.plugin.sdk.plugin;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * 插件里的「Activity」（对齐 360 的插件 Activity 写法）。
 * <p>
 * 不是真正的 android.app.Activity，而是继承 {@link PluginBaseActivity} 的普通类。
 * 生命周期由宿主的占位 Activity 转发进来，本类在 onCreate 里用插件资源 inflate 布局
 * 并 set 到宿主 Activity 上。
 * <p>
 * 其余生命周期方法（含按返回键关闭页面）由 {@link PluginBaseActivity} 提供默认实现，
 * 这里只重写关心的。
 */
public class PluginActivity extends PluginBaseActivity {

    @Override
    public void onCreate(Activity activity, Bundle savedInstanceState) {
        setContentView(activity, R.layout.plugin_activity);

        TextView version = (TextView) findViewById(R.id.tv_plugin_version);
        if (version != null) {
            version.setText("插件版本 " + PluginEntry.getVersion());
        }
    }
}
