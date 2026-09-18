package com.plugin.sdk.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager.LayoutParams;

/**
 * 插件 Activity 生命周期接口（sdk 侧定义，与插件侧同名同方法，靠反射约定，不依赖类型）。
 */
public interface ApkInterfaceForProxyActivity {

    void onCreate(Activity activity, Bundle savedInstanceState);

    void onStart();

    void onRestart();

    void onActivityResult(int requestCode, int resultCode, Intent data);

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
