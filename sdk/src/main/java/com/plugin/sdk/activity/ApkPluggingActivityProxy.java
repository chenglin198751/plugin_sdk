package com.plugin.sdk.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager.LayoutParams;

import com.plugin.sdk.utils.AppLogUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 宿主侧 Activity 代理（对齐 360 的 ApkPluggingActivityProxy）。
 * <p>
 * 通过反射加载插件里的 {@code com.plugin.sdk.plugin.ApkProxyActivity}（补丁 dex 已合并进
 * 宿主 ClassLoader），并把宿主占位 Activity 的生命周期反射转发给它。
 * <p>
 * 本类不与插件共享类型，契约是「方法名 + 参数签名」，因此插件可以完全不依赖 SDK 独立编译。
 */
public class ApkPluggingActivityProxy implements ApkInterfaceForProxyActivity {

    private static final String TAG = "ApkPluggingActivityProxy";

    private static final String PLUGIN_PROXY_ACTIVITY = "com.plugin.sdk.plugin.ApkProxyActivity";

    private Object mProxyObj;
    private Method m_onCreate;
    private Method m_onStart;
    private Method m_onRestart;
    private Method m_onActivityResult;
    private Method m_onRequestPermissionsResult;
    private Method m_onResume;
    private Method m_onPause;
    private Method m_onStop;
    private Method m_onDestroy;
    private Method m_onSaveInstanceState;
    private Method m_onNewIntent;
    private Method m_onRestoreInstanceState;
    private Method m_onTouchEvent;
    private Method m_onKeyUp;
    private Method m_onWindowAttributesChanged;
    private Method m_onWindowFocusChanged;
    private Method m_onBackPressed;
    private Method m_onCreateOptionsMenu;
    private Method m_onOptionsItemSelected;

    public void init() throws Exception {
        Class<?> clazz = Class.forName(PLUGIN_PROXY_ACTIVITY);
        mProxyObj = clazz.getDeclaredConstructor().newInstance();

        m_onCreate = clazz.getMethod("onCreate", Activity.class, Bundle.class);
        m_onStart = clazz.getMethod("onStart");
        m_onRestart = clazz.getMethod("onRestart");
        m_onActivityResult = clazz.getMethod("onActivityResult", int.class, int.class, Intent.class);
        m_onRequestPermissionsResult = clazz.getMethod("onRequestPermissionsResult", int.class, String[].class, int[].class);
        m_onResume = clazz.getMethod("onResume");
        m_onPause = clazz.getMethod("onPause");
        m_onStop = clazz.getMethod("onStop");
        m_onDestroy = clazz.getMethod("onDestroy");
        m_onSaveInstanceState = clazz.getMethod("onSaveInstanceState", Bundle.class);
        m_onNewIntent = clazz.getMethod("onNewIntent", Intent.class);
        m_onRestoreInstanceState = clazz.getMethod("onRestoreInstanceState", Bundle.class);
        m_onTouchEvent = clazz.getMethod("onTouchEvent", MotionEvent.class);
        m_onKeyUp = clazz.getMethod("onKeyUp", int.class, KeyEvent.class);
        m_onWindowAttributesChanged = clazz.getMethod("onWindowAttributesChanged", LayoutParams.class);
        m_onWindowFocusChanged = clazz.getMethod("onWindowFocusChanged", boolean.class);
        m_onBackPressed = clazz.getMethod("onBackPressed");
        m_onCreateOptionsMenu = clazz.getMethod("onCreateOptionsMenu", Menu.class);
        m_onOptionsItemSelected = clazz.getMethod("onOptionsItemSelected", MenuItem.class);
    }

    /**
     * 统一处理插件方法调用失败。
     * <p>
     * 插件页面里抛出的异常会被反射包装成 InvocationTargetException，这里拆出真实原因再打日志，
     * 否则日志里只能看到反射包装层，看不到插件真正的崩溃点。
     */
    private static void logFailure(String method, Exception e) {
        Throwable cause = (e instanceof InvocationTargetException && e.getCause() != null)
                ? e.getCause() : e;
        AppLogUtils.e(TAG, "插件 " + method + " 调用失败", cause);
    }

    @Override
    public void onCreate(Activity activity, Bundle savedInstanceState) {
        if (m_onCreate == null) return;
        try { m_onCreate.invoke(mProxyObj, activity, savedInstanceState); } catch (Exception e) { logFailure("onCreate", e); }
    }

    @Override
    public void onStart() {
        if (m_onStart == null) return;
        try { m_onStart.invoke(mProxyObj); } catch (Exception e) { logFailure("onStart", e); }
    }

    @Override
    public void onRestart() {
        if (m_onRestart == null) return;
        try { m_onRestart.invoke(mProxyObj); } catch (Exception e) { logFailure("onRestart", e); }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (m_onActivityResult == null) return;
        try { m_onActivityResult.invoke(mProxyObj, requestCode, resultCode, data); } catch (Exception e) { logFailure("onActivityResult", e); }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (m_onRequestPermissionsResult == null) return;
        try { m_onRequestPermissionsResult.invoke(mProxyObj, requestCode, permissions, grantResults); } catch (Exception e) { logFailure("onRequestPermissionsResult", e); }
    }

    @Override
    public void onResume() {
        if (m_onResume == null) return;
        try { m_onResume.invoke(mProxyObj); } catch (Exception e) { logFailure("onResume", e); }
    }

    @Override
    public void onPause() {
        if (m_onPause == null) return;
        try { m_onPause.invoke(mProxyObj); } catch (Exception e) { logFailure("onPause", e); }
    }

    @Override
    public void onStop() {
        if (m_onStop == null) return;
        try { m_onStop.invoke(mProxyObj); } catch (Exception e) { logFailure("onStop", e); }
    }

    @Override
    public void onDestroy() {
        if (m_onDestroy == null) return;
        try { m_onDestroy.invoke(mProxyObj); } catch (Exception e) { logFailure("onDestroy", e); }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (m_onSaveInstanceState == null) return;
        try { m_onSaveInstanceState.invoke(mProxyObj, outState); } catch (Exception e) { logFailure("onSaveInstanceState", e); }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (m_onNewIntent == null) return;
        try { m_onNewIntent.invoke(mProxyObj, intent); } catch (Exception e) { logFailure("onNewIntent", e); }
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (m_onRestoreInstanceState == null) return;
        try { m_onRestoreInstanceState.invoke(mProxyObj, savedInstanceState); } catch (Exception e) { logFailure("onRestoreInstanceState", e); }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (m_onTouchEvent == null) return false;
        try { return (Boolean) m_onTouchEvent.invoke(mProxyObj, event); } catch (Exception e) { logFailure("onTouchEvent", e); }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (m_onKeyUp == null) return false;
        try { return (Boolean) m_onKeyUp.invoke(mProxyObj, keyCode, event); } catch (Exception e) { logFailure("onKeyUp", e); }
        return false;
    }

    @Override
    public void onWindowAttributesChanged(LayoutParams params) {
        if (m_onWindowAttributesChanged == null) return;
        try { m_onWindowAttributesChanged.invoke(mProxyObj, params); } catch (Exception e) { logFailure("onWindowAttributesChanged", e); }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (m_onWindowFocusChanged == null) return;
        try { m_onWindowFocusChanged.invoke(mProxyObj, hasFocus); } catch (Exception e) { logFailure("onWindowFocusChanged", e); }
    }

    @Override
    public void onBackPressed() {
        if (m_onBackPressed == null) return;
        try { m_onBackPressed.invoke(mProxyObj); } catch (Exception e) { logFailure("onBackPressed", e); }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (m_onCreateOptionsMenu == null) return false;
        try { return (Boolean) m_onCreateOptionsMenu.invoke(mProxyObj, menu); } catch (Exception e) { logFailure("onCreateOptionsMenu", e); }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (m_onOptionsItemSelected == null) return false;
        try { return (Boolean) m_onOptionsItemSelected.invoke(mProxyObj, item); } catch (Exception e) { logFailure("onOptionsItemSelected", e); }
        return false;
    }
}
