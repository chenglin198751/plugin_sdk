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
 * 插件 Activity 生命周期接口（对齐 360 的 ApkInterfaceForProxyActivity）。
 * <p>
 * 插件里的「Activity」不是真正的 android.app.Activity，而是实现本接口的普通类，
 * 由宿主占位 Activity（HostProxyActivity）通过反射把生命周期转发进来。
 */
public interface ApkInterfaceForProxyActivity {

    void onCreate(Activity activity, Bundle savedInstanceState);

    void onStart();

    void onRestart();

    void onActivityResult(int requestCode, int resultCode, Intent data);

    void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults);

    void onResume();

    void onPause();

    void onStop();

    void onDestroy();

    void onSaveInstanceState(Bundle outState);

    void onNewIntent(Intent intent);

    void onRestoreInstanceState(Bundle savedInstanceState);

    boolean onTouchEvent(MotionEvent event);

    boolean onKeyUp(int keyCode, KeyEvent event);

    void onWindowAttributesChanged(LayoutParams params);

    void onWindowFocusChanged(boolean hasFocus);

    void onBackPressed();

    boolean onCreateOptionsMenu(Menu menu);

    boolean onOptionsItemSelected(MenuItem item);
}
