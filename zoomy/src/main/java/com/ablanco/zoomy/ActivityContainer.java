package com.ablanco.zoomy;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Created by Álvaro Blanco Cabrero on 01/05/2017.
 * Zoomy.
 */

public class ActivityContainer implements TargetContainer {

    private Activity mActivity;
    private ViewGroup mRootView;

    ActivityContainer(Activity activity) {
        this.mActivity = activity;
    }

    @Override
    public ViewGroup getDecorView() {
        return (ViewGroup) mActivity.getWindow().getDecorView();
    }

    @Override
    public ViewGroup getRootView() {
        if (mRootView == null) {
            mRootView = new SafeRootView(mActivity);
            getDecorView().addView(mRootView);
        }
        return mRootView;
    }

}
