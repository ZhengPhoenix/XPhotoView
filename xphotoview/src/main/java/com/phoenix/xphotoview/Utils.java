package com.phoenix.xphotoview;

import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Created by zhenghui on 2017/5/23.
 */

public class Utils {

    public static RectF rectMulti(Rect r, float ratio)
    {
        return rectMulti(new RectF(r), ratio);
    }

    public static RectF rectMulti(RectF r, float ratio)
    {
        float left = r.left * ratio;
        float top = r.top * ratio;
        float right = left + r.width() * ratio;
        float bottom = top + r.height() * ratio;

        return new RectF(left, top, right, bottom);
    }
}
