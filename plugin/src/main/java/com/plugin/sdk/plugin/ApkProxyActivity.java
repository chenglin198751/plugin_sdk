package com.plugin.sdk.plugin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager.LayoutParams;
import android.util.Log;

/**
 * 插件侧的 Activity 代理（对齐 360 的 ApkProxyActivity）。
 * <p>
 * 宿主占位 Activity（HostProxyActivity）通过反射调用本类的生命周期方法，
 * 本类根据 Intent 里的 viewId 创建对应的插件「Activity」类并转发生命周期。
 */
public class ApkProxyActivity implements ApkInterfaceForProxyActivity {

    private static final String TAG = "ApkProxyActivity";

    /** 主页面标识，需与宿主 PluginSdk.DEFAULT_PLUGIN_VIEW_ID 保持一致。 */
    public static final int VIEW_ID_MAIN = 1;

    /** Intent 里携带的 viewId key，用于区分插件内多个 Activity。 */
    public static final String EXTRA_VIEW_ID = "plugin_view_id";

    private ApkInterfaceForProxyActivity mCurrentView;

    @Override
    public void onCreate(Activity activity, Bundle savedInstanceState) {
        if (activity == null) {
            return;
        }
        int viewId = readViewId(activity);
        mCurrentView = createView(viewId);
        if (mCurrentView == null) {
            // 未注册的 viewId：明确报错并关闭页面。
            // 如果不处理，宿主会留下一个空白页，而且返回键也退不出去。
            Log.e(TAG, "未注册的 " + EXTRA_VIEW_ID + "=" + viewId + "，关闭插件页面");
            activity.finish();
            return;
        }
        Log.i(TAG, "创建插件页面 viewId=" + viewId + ", class=" + mCurrentView.getClass().getName());
        mCurrentView.onCreate(activity, savedInstanceState);
    }

    private int readViewId(Activity activity) {
        int viewId = VIEW_ID_MAIN;
        try {
            Intent intent = activity.getIntent();
            if (intent != null) {
                viewId = intent.getIntExtra(EXTRA_VIEW_ID, VIEW_ID_MAIN);
            }
        } catch (Exception e) {
            Log.e(TAG, "读取 " + EXTRA_VIEW_ID + " 失败，回退到默认页面 viewId=" + viewId, e);
        }
        return viewId > 0 ? viewId : VIEW_ID_MAIN;
    }

    /** 插件页面注册表：新增插件页面时在这里加分支。 */
    private ApkInterfaceForProxyActivity createView(int viewId) {
        switch (viewId) {
            case VIEW_ID_MAIN:
                return new PluginActivity();
            default:
                return null;
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (mCurrentView != null) mCurrentView.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
