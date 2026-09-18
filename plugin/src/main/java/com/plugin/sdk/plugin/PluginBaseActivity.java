package com.plugin.sdk.plugin;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

/**
 * 插件 Activity 基类（对齐 360 的 PluginBaseActivity）。
 * <p>
 * 不继承 android.app.Activity，而是持有宿主的 Activity 和插件 inflate 出来的根 View。
 * setContentView / findViewById 都基于插件的独立 Resources。
 */
public class PluginBaseActivity {

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
}
