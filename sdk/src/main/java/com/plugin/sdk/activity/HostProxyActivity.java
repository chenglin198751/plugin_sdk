package com.plugin.sdk.activity;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager.LayoutParams;

import com.plugin.sdk.utils.AppLogUtils;
import com.plugin.sdk.utils.EdgeToEdge;

/**
 * 宿主占位 Activity（对齐 360 的 HostProxyActivity）。
 * <p>
 * 这是唯一在宿主 manifest 里注册的 Activity，用来承载插件里的「Activity」。
 * 启动插件 Activity 时，实际启动的是本类，由本类把生命周期转发给插件里的
 * ApkProxyActivity（通过 {@link ApkPluggingActivityProxy} 反射调用）。
 */
public class HostProxyActivity extends Activity {

    private static final String TAG = "HostProxyActivity";

    private ApkPluggingActivityProxy mActivityProxy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        try {
            mActivityProxy = new ApkPluggingActivityProxy();
            mActivityProxy.init();
            mActivityProxy.onCreate(this, savedInstanceState);
        } catch (Throwable t) {
            AppLogUtils.e(TAG, "创建插件 Activity 代理失败", t);
            finish();
        }
    }

    @Override
    protected void onStart() {
        if (mActivityProxy != null) mActivityProxy.onStart();
        super.onStart();
    }

    @Override
    protected void onRestart() {
        if (mActivityProxy != null) mActivityProxy.onRestart();
        super.onRestart();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (mActivityProxy != null) mActivityProxy.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        if (mActivityProxy != null) mActivityProxy.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (mActivityProxy != null) mActivityProxy.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mActivityProxy != null) mActivityProxy.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mActivityProxy != null) mActivityProxy.onDestroy();
        mActivityProxy = null;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mActivityProxy != null) mActivityProxy.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        if (mActivityProxy != null) mActivityProxy.onNewIntent(intent);
        super.onNewIntent(intent);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        if (mActivityProxy != null) mActivityProxy.onRestoreInstanceState(savedInstanceState);
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mActivityProxy != null) return mActivityProxy.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mActivityProxy != null && mActivityProxy.onKeyUp(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onWindowAttributesChanged(LayoutParams params) {
        if (mActivityProxy != null) mActivityProxy.onWindowAttributesChanged(params);
        super.onWindowAttributesChanged(params);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (mActivityProxy != null) mActivityProxy.onWindowFocusChanged(hasFocus);
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void onBackPressed() {
        if (mActivityProxy != null) {
            mActivityProxy.onBackPressed();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mActivityProxy != null) return mActivityProxy.onCreateOptionsMenu(menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mActivityProxy != null) return mActivityProxy.onOptionsItemSelected(item);
        return super.onOptionsItemSelected(item);
    }
}
