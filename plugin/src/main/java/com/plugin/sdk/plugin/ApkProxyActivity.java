package com.plugin.sdk.plugin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager.LayoutParams;

/**
 * 插件侧的 Activity 代理（对齐 360 的 ApkProxyActivity）。
 * <p>
 * 宿主占位 Activity（HostProxyActivity）通过反射调用本类的生命周期方法，
 * 本类根据 Intent 里的 viewId 创建对应的插件「Activity」类并转发生命周期。
 */
public class ApkProxyActivity implements ApkInterfaceForProxyActivity {

    /** Intent 里携带的 viewId key，用于区分插件内多个 Activity。 */
    public static final String EXTRA_VIEW_ID = "plugin_view_id";

    private ApkInterfaceForProxyActivity mCurrentView;

    @Override
    public void onCreate(Activity activity, Bundle savedInstanceState) {
        if (activity == null) {
            return;
        }
        int viewId = 1;
        try {
            Intent intent = activity.getIntent();
            if (intent != null) {
                viewId = intent.getIntExtra(EXTRA_VIEW_ID, 1);
            }
        } catch (Exception ignored) {
        }
        if (viewId <= 0) {
            viewId = 1;
        }

        if (viewId == 1) {
            mCurrentView = new PluginActivity();
        }

        if (mCurrentView != null) {
            mCurrentView.onCreate(activity, savedInstanceState);
        }
    }

    @Override
    public void onStart() {
        if (mCurrentView != null) mCurrentView.onStart();
    }

    @Override
    public void onRestart() {
        if (mCurrentView != null) mCurrentView.onRestart();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mCurrentView != null) mCurrentView.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onResume() {
        if (mCurrentView != null) mCurrentView.onResume();
    }

    @Override
    public void onPause() {
        if (mCurrentView != null) mCurrentView.onPause();
    }

    @Override
    public void onStop() {
        if (mCurrentView != null) mCurrentView.onStop();
    }

    @Override
    public void onDestroy() {
        if (mCurrentView != null) mCurrentView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mCurrentView != null) mCurrentView.onSaveInstanceState(outState);
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (mCurrentView != null) mCurrentView.onNewIntent(intent);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (mCurrentView != null) mCurrentView.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mCurrentView != null) return mCurrentView.onTouchEvent(event);
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mCurrentView != null) return mCurrentView.onKeyUp(keyCode, event);
        return false;
    }

    @Override
    public void onWindowAttributesChanged(LayoutParams params) {
        if (mCurrentView != null) mCurrentView.onWindowAttributesChanged(params);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (mCurrentView != null) mCurrentView.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void onBackPressed() {
        if (mCurrentView != null) mCurrentView.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mCurrentView != null) return mCurrentView.onCreateOptionsMenu(menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mCurrentView != null) return mCurrentView.onOptionsItemSelected(item);
        return false;
    }
}
