package com.ablanco.zoomy;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.media.ImageReader;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

/**
 * Created by Álvaro Blanco Cabrero on 12/02/2017.
 * Zoomy.
 */

class ZoomableTouchListener implements View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {

    private static final int STATE_IDLE = 0;
    private static final int STATE_POINTER_DOWN = 1;
    private static final int STATE_ZOOMING = 2;

    private static final float MIN_SCALE_FACTOR = 1f;
    private static final float MAX_SCALE_FACTOR = 5f;
    private final TapListener mTapListener;
    private final LongPressListener mLongPressListener;
    private final DoubleTapListener mDoubleTapListener;
    private String mFilePath;
    private int mState = STATE_IDLE;
    private TargetContainer mTargetContainer;
    private View mTarget;
    private ImageView mZoomableView;
    private SubsamplingScaleImageView mSubZoomableView;

    private View mShadow;
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetector mGestureDetector;
    private GestureDetector.SimpleOnGestureListener mGestureListener =
            new GestureDetector.SimpleOnGestureListener() {

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (mTapListener != null) {
                        mTapListener.onTap(mTarget);
                    }
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (mLongPressListener != null) {
                        mLongPressListener.onLongPress(mTarget);
                    }
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (mDoubleTapListener != null) {
                        mDoubleTapListener.onDoubleTap(mTarget);
                    }
                    return true;
                }
            };
    private float mScaleFactor = 1f;
    private PointF mCurrentMovementMidPoint = new PointF();
    private PointF mInitialPinchMidPoint = new PointF();
    private Point mTargetViewCords = new Point();
    private boolean mAnimatingZoomEnding = false;
    private Interpolator mEndZoomingInterpolator;
    private ZoomyConfig mConfig;
    private ZoomListener mZoomListener;
    private Runnable mEndingZoomAction = new Runnable() {
        @Override
        public void run() {
            try {
                removeFromDecorView(mShadow);
                if (mZoomableView != null) {
                    removeFromDecorView(mZoomableView);
                }
                if (mSubZoomableView != null) {
                    removeFromDecorView(mSubZoomableView);
                }
                mTarget.setVisibility(View.VISIBLE);
                mZoomableView = null;
                mSubZoomableView = null;
                mCurrentMovementMidPoint = new PointF();
                mInitialPinchMidPoint = new PointF();
                mAnimatingZoomEnding = false;
                mState = STATE_IDLE;
                if (mZoomListener != null) {
                    mZoomListener.onViewEndedZooming(mTarget);
                }
                if (mConfig.isImmersiveModeEnabled()) {
                    showSystemUI();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };


    ZoomableTouchListener(TargetContainer targetContainer,
                          View view,
                          ZoomyConfig config,
                          Interpolator interpolator,
                          ZoomListener zoomListener,
                          TapListener tapListener,
                          LongPressListener longPressListener,
                          DoubleTapListener doubleTapListener,
                          String filePath) {
        this.mTargetContainer = targetContainer;
        this.mTarget = view;
        this.mConfig = config;
        this.mEndZoomingInterpolator = interpolator != null
                ? interpolator : new AccelerateDecelerateInterpolator();
        this.mScaleGestureDetector = new ScaleGestureDetector(view.getContext(), this);
        this.mGestureDetector = new GestureDetector(view.getContext(), mGestureListener);
        this.mZoomListener = zoomListener;
        this.mTapListener = tapListener;
        this.mLongPressListener = longPressListener;
        this.mDoubleTapListener = doubleTapListener;
        this.mFilePath = filePath;
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        int action = ev.getAction() & MotionEvent.ACTION_MASK;
        Log.i("ZOOMTouch", MotionEvent.actionToString(action));
        Log.i("ZOOMmState", mState + "");
        Log.i("ZOOMmPointCount", ev.getPointerCount() + "");
        if (mAnimatingZoomEnding) {
            return true;
        }
        if (ev.getPointerCount() > 2) {
            if (mState == STATE_ZOOMING && (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP)) {
                //三个指头以上同时离开时继续执行下一步
            } else {
                return true;
            }
        }

        mScaleGestureDetector.onTouchEvent(ev);
        mGestureDetector.onTouchEvent(ev);
        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN:
                switch (mState) {
                    case STATE_IDLE:
                        mState = STATE_POINTER_DOWN;
                        break;
                    case STATE_POINTER_DOWN:
                        mState = STATE_ZOOMING;
                        MotionUtils.midPointOfEvent(mInitialPinchMidPoint, ev);
                        startZoomingView(mTarget);
                        break;
                    default:
                        break;
                }
                break;

            case MotionEvent.ACTION_MOVE:

                if (mState == STATE_ZOOMING) {
                    MotionUtils.midPointOfEvent(mCurrentMovementMidPoint, ev);
                    //because our initial pinch could be performed in any of the view edges,
                    //we need to substract this difference and add system bars height
                    //as an offset to avoid an initial transition jump
                    float currentCenterY = mCurrentMovementMidPoint.y;
                    mCurrentMovementMidPoint.x -= mInitialPinchMidPoint.x;
                    //because previous function returns the midpoint for relative X,Y coords,
                    //we need to add absolute view coords in order to ensure the correct position
                    mCurrentMovementMidPoint.x += mTargetViewCords.x;
                    float x = mCurrentMovementMidPoint.x;
                    if (mZoomableView != null) {
                        float maginHeight = ((mZoomableView.getHeight() / 2 - mInitialPinchMidPoint.y)) * (mScaleFactor - 1);
                        mZoomableView.setX(x);
                        mZoomableView.setY(mTargetViewCords.y + maginHeight + (currentCenterY - mInitialPinchMidPoint.y));
                    }
                    if (mSubZoomableView != null) {
                        float maginHeight = ((mSubZoomableView.getHeight() / 2 - mInitialPinchMidPoint.y)) * (mScaleFactor - 1);
                        mSubZoomableView.setX(x);
                        mSubZoomableView.setY(mTargetViewCords.y + maginHeight + (currentCenterY - mInitialPinchMidPoint.y));
                    }

                }

                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                switch (mState) {
                    case STATE_ZOOMING:
                        endZoomingView();
                        break;
                    case STATE_POINTER_DOWN:
                        mState = STATE_IDLE;
                        break;
                    default:
                        break;
                }

                break;
            default:
                break;
        }

        return true;
    }


    private void endZoomingView() {
        if (mConfig.isZoomAnimationEnabled()) {
            mAnimatingZoomEnding = true;
            if (mZoomableView != null) {
                mZoomableView.animate()
                        .x(mTargetViewCords.x)
                        .y(mTargetViewCords.y)
                        .scaleX(1)
                        .scaleY(1)
                        .setInterpolator(mEndZoomingInterpolator)
                        .withEndAction(mEndingZoomAction).start();
            } else if (mSubZoomableView != null) {
                mSubZoomableView.animate()
                        .x(mTargetViewCords.x)
                        .y(mTargetViewCords.y)
                        .scaleX(1)
                        .scaleY(1)
                        .setInterpolator(mEndZoomingInterpolator)
                        .withEndAction(mEndingZoomAction).start();
            }
        } else {
            mEndingZoomAction.run();
        }
    }


    private void startZoomingView(View view) {
        if (mTarget instanceof SubsamplingScaleImageView) {
            mSubZoomableView = new SubsamplingScaleImageView(mTarget.getContext());
            mSubZoomableView.setLayoutParams(new ViewGroup.LayoutParams(mTarget.getWidth(), mTarget.getHeight()));
            if (mFilePath != null) {
//                mSubZoomableView.setImage(ImageSource.bitmap(ViewUtils.getBitmapFromView(mTarget)));
                mSubZoomableView.setImage(ImageSource.uri(mFilePath));
            }
            //show the view in the same coords
            mTargetViewCords = ViewUtils.getViewAbsoluteCords(view);

            mSubZoomableView.setX(mTargetViewCords.x);
            mSubZoomableView.setY(mTargetViewCords.y);
        } else {
            mZoomableView = new ImageView(mTarget.getContext());
            mZoomableView.setLayoutParams(new ViewGroup.LayoutParams(mTarget.getWidth(), mTarget.getHeight()));
            mZoomableView.setImageBitmap(ViewUtils.getBitmapFromView(view));

            //show the view in the same coords
            mTargetViewCords = ViewUtils.getViewAbsoluteCords(view);

            mZoomableView.setX(mTargetViewCords.x);
            mZoomableView.setY(mTargetViewCords.y);
        }


        if (mShadow == null) {
            mShadow = new View(mTarget.getContext());
        }
        mShadow.setBackgroundResource(0);

        addToDecorView(mShadow);
        if (mZoomableView != null) {
            addToDecorView(mZoomableView);
        } else if (mSubZoomableView != null) {
            addToDecorView(mSubZoomableView);
        }

        //trick for simulating the view is getting out of his parent
        disableParentTouch(mTarget.getParent());
        mTarget.setVisibility(View.INVISIBLE);

        if (mConfig.isImmersiveModeEnabled()) {
            hideSystemUI();
        }
        if (mZoomListener != null) {
            mZoomListener.onViewStartedZooming(mTarget);
        }
    }


    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (mZoomableView == null && mSubZoomableView == null) {
            return false;
        }

        mScaleFactor *= detector.getScaleFactor();

        // Don't let the object get too large.
        mScaleFactor = Math.max(MIN_SCALE_FACTOR, Math.min(mScaleFactor, MAX_SCALE_FACTOR));
        if (mZoomableView != null) {
            mZoomableView.setScaleX(mScaleFactor);
            mZoomableView.setScaleY(mScaleFactor);
        } else if (mSubZoomableView != null) {
            mSubZoomableView.setScaleX(mScaleFactor);
            mSubZoomableView.setScaleY(mScaleFactor);
        }
        obscureDecorView(mScaleFactor);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return mZoomableView != null || mSubZoomableView != null;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        mScaleFactor = 1f;
    }

    private void addToDecorView(View v) {
        mTargetContainer.getRootView().addView(v);
    }

    private void removeFromDecorView(View v) {
        mTargetContainer.getRootView().removeView(v);
    }

    private void obscureDecorView(float factor) {
        //normalize value between 0 and 1
        float normalizedValue = (factor - MIN_SCALE_FACTOR) / (MAX_SCALE_FACTOR - MIN_SCALE_FACTOR);
        normalizedValue = Math.min(0.75f, normalizedValue * 2);
        int obscure = Color.argb((int) (normalizedValue * 255), 0, 0, 0);
        mShadow.setBackgroundColor(obscure);
    }

    private void hideSystemUI() {
        mTargetContainer.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN); // hide status ba;
    }

    private void showSystemUI() {
        mTargetContainer.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void disableParentTouch(ViewParent view) {
        view.requestDisallowInterceptTouchEvent(true);
        if (view.getParent() != null) {
            disableParentTouch((view.getParent()));
        }
    }
}
