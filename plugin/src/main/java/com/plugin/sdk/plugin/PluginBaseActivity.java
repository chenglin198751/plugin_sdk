package com.plugin.sdk.plugin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;

/**
 * 插件 Activity 基类（对齐 360 的 PluginBaseActivity）。
 * <p>
 * 不继承 android.app.Activity，而是持有宿主的 Activity 和插件 inflate 出来的根 View。
 * setContentView / findViewById 都基于插件的独立 Resources。
 * <p>
 * 本类已实现 {@link ApkInterfaceForProxyActivity} 并给出全部空默认实现，
 * 因此插件页面只需要重写自己真正关心的方法（通常只有 onCreate）。
 */
public class PluginBaseActivity implements ApkInterfaceForProxyActivity {

    private static final String TAG = "PluginBaseActivity";

    protected Activity mActivity = null;
    protected View mBaseView = null;

    public Activity getActivity() {
        return mActivity;
    }

    public View getBaseView() {
        return mBaseView;
    }

    /** 用插件资源 inflate 布局，并 set 到宿主的 Activity 上。 */
    protected void setContentView(Activity activity, int layoutId) {
        try {
            if (activity == null) {
                return;
            }
            mActivity = activity;
            mBaseView = PluginResources.inflate(activity, layoutId);
            if (mBaseView == null) {
                return;
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            activity.setContentView(mBaseView, lp);
        } catch (Throwable t) {
            Log.e(TAG, "插件页面 setContentView 失败", t);
        }
    }

    public View findViewById(int id) {
        if (mBaseView == null) {
            return null;
        }
        return mBaseView.findViewById(id);
    }

    // ==================== 生命周期默认实现 ====================
    // 插件页面按需重写，不需要像以前那样把 18 个方法全抄一遍。

    @Override
    public void onCreate(Activity activity, Bundle savedInstanceState) {
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

    /**
     * 默认按返回键关闭当前插件页面。
     * <p>
     * 这里必须给出默认行为：宿主 {@code HostProxyActivity} 把返回键转发给插件后
     * 就直接返回，不会再回退到 {@code Activity.onBackPressed()}。如果本方法是空实现，
     * 任何没有重写它的插件页面都会出现"按返回键无反应、退不出去"的问题。
     * 需要二次确认等自定义逻辑的页面自行重写本方法。
     */
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
