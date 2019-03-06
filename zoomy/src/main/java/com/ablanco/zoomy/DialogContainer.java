package com.ablanco.zoomy;

import android.app.Dialog;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Created by Álvaro Blanco Cabrero on 01/05/2017.
 * Zoomy.
 */

public class DialogContainer implements TargetContainer {

    private Dialog mDialog;
    private FrameLayout mRootView;

    DialogContainer(Dialog dialog) {
        this.mDialog = dialog;
    }

    @Override
    public final ViewGroup getDecorView() {
        return mDialog.getWindow() != null ? (ViewGroup) mDialog.getWindow().getDecorView() : null;
    }

    @Override
    public ViewGroup getRootView() {
        if (mRootView == null) {
            mRootView = new SafeRootView(mDialog.getContext());
            ((ViewGroup) mDialog.getWindow().getDecorView()).addView(mRootView);
        }
        return mRootView;
    }
}
