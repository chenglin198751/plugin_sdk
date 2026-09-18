package com.plugin.sdk.plugin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager.LayoutParams;
import android.widget.TextView;

/**
 * 插件里的「Activity」（对齐 360 的插件 Activity 写法）。
 * <p>
 * 不是真正的 android.app.Activity，而是继承 {@link PluginBaseActivity}、实现
 * {@link ApkInterfaceForProxyActivity} 的普通类。生命周期由宿主的占位 Activity
 * 转发进来，本类在 onCreate 里用插件资源 inflate 布局并 set 到宿主 Activity 上。
 */
public class PluginActivity extends PluginBaseActivity implements ApkInterfaceForProxyActivity {

    @Override
    public void onCreate(Activity activity, Bundle savedInstanceState) {
        setContentView(activity, R.layout.plugin_activity);

        TextView version = (TextView) findViewById(R.id.tv_plugin_version);
        if (version != null) {
            version.setText("补丁版本 " + PluginEntry.getVersion());
        }
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onRestart() {
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onStop() {
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public void onWindowAttributesChanged(LayoutParams params) {
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
    }

    @Override
    public void onBackPressed() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }
}
