package com.ablanco.zoomy;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;

/**
 * Created by Gao Xuefeng
 * on 6/3/2019
 */
public class SafeRootView extends FrameLayout {
    public SafeRootView(Context context) {
        super(context);
    }

    public SafeRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SafeRootView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        try {
            super.onLayout(changed, left, top, right, bottom);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("SafeRootView", "发生崩溃");
        }

    }
}
