package com.phoenix.xphotoview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;

/**
 * Created by zhenghui on 2017/5/19.
 */
public class GestureManager extends
        GestureDetector.SimpleOnGestureListener implements ScaleGestureDetector.OnScaleGestureListener {
    public final static boolean DEBUG = true;
    public final static String TAG = "GestureManager";

    /**
     * 默认双击放大的时间
     */
    private final static int DOUBLE_SCALE_TIME = 400;


    private IViewAttacher mBM = null;

    private IXphotoView mXImageView = null;

    private XGestureDetector mGestureDetector = null;

    public GestureManager(Context context, IXphotoView xiv,
                          IViewAttacher ibm) {
        mBM = ibm;
        mXImageView = xiv;

        mGestureDetector = new XGestureDetector(context, this);
    }

    public boolean onTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
    }


    private class XGestureDetector extends GestureDetector {
        /**
         * 放大手势检测
         */
        private ScaleGestureDetector mScaleDetector = null;

        public XGestureDetector(Context context, GestureManager listener)
        {
            super(context, listener);

            float density = context.getResources().getDisplayMetrics().density;
            float dpi = density * 160.0f;
            mPhysicalCoeff = SensorManager.GRAVITY_EARTH  * 39.37f * dpi * 0.84f;

            mScaleDetector = new ScaleGestureDetector(context, listener);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            stopFling();
            boolean scaleResult = mScaleDetector.onTouchEvent(event);
            boolean gestureResult = super.onTouchEvent(event);
            return scaleResult || gestureResult;
        }
    }



    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        int x = (int) e.getX();
        int y = (int) e.getY();
        if (DEBUG) {
            Log.e(TAG, "On Tapped: X: " + x + " Y: " + y + " Is: " + (mBM != null && mBM.isTapOnImage(x, y)));
        }

        if(mXImageView != null) {
            mXImageView.onSingleTab();
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (mBM == null) {
            return false;
        }

        boolean handled = false;
//        if (mActionListener != null) {
//            handled = mActionListener.onDoubleTapped(mXImageView.getInstance(), e);
//        }
        if (!handled) {
            int x = (int) e.getX();
            int y = (int) e.getY();
            mBM.doubleTapScale(x, y, true, DOUBLE_SCALE_TIME);
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if(mXImageView != null) {
            mXImageView.onLongTab();
        }
    }

    /*************************************滑动****************************************/
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (mBM == null) {
            return false;
        }

        int state = mBM.move((int) -distanceX, (int) -distanceY);

        if ((state & PhotoViewAttacher.LEFT) == PhotoViewAttacher.LEFT ||
                (state & PhotoViewAttacher.RIGHT) == PhotoViewAttacher.RIGHT) {
            mXImageView.interceptParentTouchEvent(false);
        }

        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        startFling(velocityX * 1.2f, velocityY * 1.2f);
        return true;
    }

    /*************************************缩放**************************************/

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (mBM == null) {
            return false;
        }

        float factor = detector.getScaleFactor();
        mBM.scale(detector.getFocusX(), detector.getFocusY(), factor);

        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        /**
         * 当缩放结束后，计算最新的的SampleSize, 如果SampleSize改变了，则重新解码最新的bitmap
         */
        if (mBM != null) {
            mBM.updateSampleSize();
        }
    }


    /**********************************滑动惯性*******************************/

    private float mPhysicalCoeff;
    private float mFlingFriction = ViewConfiguration.getScrollFriction();
    private final static float DECELERATION_RATE = (float) (Math.log(0.78) / Math.log(0.9));
    private final static float INFLEXION = 0.35f;

    private ValueAnimator mValueAnimator = null;

    private void stopFling() {
        if (mValueAnimator != null) {
            mValueAnimator.cancel();
        }
    }

    private void startFling(final float velocityX, final float velocityY) {
        stopFling();

        final float fx = (velocityX < 0 ? 1 : -1);
        final float fy = (velocityY < 0 ? 1 : -1);

        final float velocity = (float) Math.hypot(velocityX, velocityY);
        final long duration = getSplineFlingDuration(velocity);

        mValueAnimator = ValueAnimator.ofFloat(1f, 0);
        mValueAnimator.setInterpolator(new LinearInterpolator());
        mValueAnimator.setDuration(duration);
        mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private Double mLastDisX = Double.NaN;
            private Double mLastDisY = Double.NaN;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();

                double curDisX = getSplineFlingDistance(value * velocityX) * fx;
                double curDisY = getSplineFlingDistance(value * velocityY) * fy;

                if (mLastDisX.isNaN() || mLastDisY.isNaN()) {
                    mLastDisX = curDisX;
                    mLastDisY = curDisY;
                    return;
                }

                int dx = (int) (curDisX - mLastDisX);
                int dy = (int) (curDisY - mLastDisY);

//                Log.e(TAG, "Dx: " + dx + "  DY: " + dy);

                if (mBM != null) {
                    mBM.move(dx, dy);
                }

                mLastDisX = curDisX;
                mLastDisY = curDisY;
            }
        });

        mValueAnimator.start();
    }

    private double getSplineDeceleration(float velocity) {
        return Math.log(INFLEXION * Math.abs(velocity) / (mFlingFriction * mPhysicalCoeff));
    }

    private int getSplineFlingDuration(float velocity) {
        final double l = getSplineDeceleration(velocity);
        final double decelMinusOne = DECELERATION_RATE - 1.0;
        return (int) (1000.0 * Math.exp(l / decelMinusOne));
    }

    private double getSplineFlingDistance(float velocity) {
        final double l = getSplineDeceleration(velocity);
        final double decelMinusOne = DECELERATION_RATE - 1.0;
        return mFlingFriction * mPhysicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne * l);
    }
}

