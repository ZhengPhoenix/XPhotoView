package com.phoenix.xphotoview;

/**
 * Created by zhenghui on 2017/6/26.
 */

public class XPhotoViewListener {

    public interface OnXPhotoLoadListener {
        void onImageLoadStart(XPhotoView view);
        void onImageLoaded(XPhotoView view);
    }
}
