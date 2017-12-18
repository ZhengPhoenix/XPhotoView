package com.phoenix.xphotoview;

/**
 * Created by zhenghui on 2017/5/19.
 */

public class PhotoViewInterface {

    /***
     * Attacher 要处理一般 单指/双指 动作时需要实现的接口
     */
    public interface IMoveGestureInterface {
        /**
         * 移动显示的 Bitmap
         *
         * @param dx x 轴移动的差值
         * @param dy y 轴移动的差值
         * @return 返回到达的边界的与值
         */
        int onDrag(float dx, float dy);

        /***
         * 滑动显示的 Bitmap
         * @param startX
         * @param startY
         * @param velocityX
         * @param velocityY
         */
        void onFling(float startX, float startY, float velocityX, float velocityY);

        /**
         * 缩放显示的 Bitmap
         *
         * @param cx    缩放点x
         * @param cy    缩放点y
         * @param scale 缩放倍数
         */
        void onScale(float cx, float cy, float scale);

        /**
         * 缩放到指定的大小
         *
         * @param cx     中心点
         * @param cy     中心点
         * @param factor 目标缩放倍数
         */
        void scaleTo(final int cx, final int cy, float factor);

        /***
         * 缩放结束后通知 Bitmap
         * @param scaleFactor   缩放倍数
         * @param focusX        缩放中心点
         * @param focusY        缩放中心点
         */
        void onScaleEnd(float scaleFactor, float focusX, float focusY);
    }

    /***
     * Attacher 要处理 点击 动作时需要实现的接口
     */
    public interface ISimpleGestureInterface {

        /***
         * 双击放大/缩小显示的 Bitmap
         * @param cx        点击X
         * @param cy        点击Y
         * @param scale     目标缩放倍数
         */
        void onDoubleTap(float cx, float cy, float scale);

        /***
         * 响应 Bitmap 的点击事件
         * @param cx        点击X
         * @param cy        点击Y
         */
        void onSingleTap(float cx, float cy);
    }
}

