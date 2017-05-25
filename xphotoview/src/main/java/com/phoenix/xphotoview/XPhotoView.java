package com.phoenix.xphotoview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by zhenghui on 2017/5/19.
 */

public class XPhotoView extends View implements IXphotoView{

    private IViewAttacher mPhotoAttacher;

    private GestureManager mGestureManager;

    private DoubleTabScale mDefaultDoubleTabScale;

    private boolean sScaleEnable = true;

    public XPhotoView(Context context) {
        this(context, null, 0);
    }

    public XPhotoView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public XPhotoView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs);
        mPhotoAttacher = new PhotoViewAttacher(this);
        mGestureManager = new GestureManager(this.getContext(), this, mPhotoAttacher);
    }

    /**
     * 获取默认配置属性，如 ScaleType 等*/
    private void initialize(Context context, AttributeSet attrs) {
        mDefaultDoubleTabScale = DoubleTabScale.CENTER_CROP;
        if(attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.XPhotoView);
            mDefaultDoubleTabScale = DoubleTabScale.valueOf(typedArray.getInt(R.styleable.XPhotoView_scaleType, DoubleTabScale.CENTER_CROP.value));
            typedArray.recycle();
        }
    }

    public void setScaleEnable(boolean flag) {
        sScaleEnable = flag;
    }

    public void setImageResource(@DrawableRes int resId) {
        Drawable drawable = this.getContext().getResources().getDrawable(resId);
        if(drawable == null) {
            setImage((InputStream) null);
            return;
        }

        this.setImageDrawable(drawable);
    }


    public void setImageDrawable(@Nullable Drawable drawable) {
        this.setImage(((BitmapDrawable)drawable).getBitmap());
    }

    public void setImage(Bitmap image) {
        mPhotoAttacher.setBitmap(image, false);
    }

    public void setImage(String path) {
        setImage(new File(path));
    }

    public void setImage(File file) {
        if(file == null || !file.exists()) {
            setImage((InputStream) null);
            return;
        }

        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            setImage(fileInputStream);
        } catch (FileNotFoundException exp) {

        }
    }

    public void setImage(InputStream ios) {
        mPhotoAttacher.setInputStream(ios, Bitmap.Config.RGB_565);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (sScaleEnable) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mPhotoAttacher != null && !mPhotoAttacher.isNotAvailable()) {
                        interceptParentTouchEvent(true);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    break;

                case MotionEvent.ACTION_UP:
                    interceptParentTouchEvent(false);
                    break;
            }
        }

        return mGestureManager.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPhotoAttacher.draw(canvas, getWidth(), getHeight());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mPhotoAttacher.onViewSizeChanged(w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPhotoAttacher.destroy();
    }

    @Override
    public DoubleTabScale getDoubleTabScale() {
        return mDefaultDoubleTabScale;
    }

    @Override
    public String getCachedDir() {
        return null;
    }

    @Override
    public void onImageSetFinished(boolean finished) {

    }

    @Override
    public void callPostInvalidate() {
        postInvalidate();
    }

    @Override
    public void onSetImageFinished(IViewAttacher bm, boolean success, Rect image) {

    }

    @Override
    public void interceptParentTouchEvent(boolean intercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(intercept);
        }
    }
}
