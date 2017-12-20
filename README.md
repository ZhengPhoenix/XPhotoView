#XPhotoView
不少安卓开发者都有图片加载的处理经验，比如通过压缩节省图片加载中对内存的消耗。
我们经常做的是把一张1280之类大小的图片以适应屏幕大小的尺寸展现出来，同时能够通过缩放来观察。
不过这是一般水平，通过压缩来处理的话通常会导致在最大尺寸放大后看不清细节，比如拿到一张苍老师...哦不，拿到一张清明上河图，或者一张世界地图，这个时候我们要保证在最大限度的放大后仍然能够看清楚每个人物每个城市，一般的压缩的方案就不合适了。

这里我们要讨论的是如何用局部解析(BitmapRegionDecoder)来做到在不占用过多内存的情况下实现超大图的缩放。

惯例贴源码：[XPhotoView Demo](https://github.com/ZhengPhoenix/XPhotoView)

XPhotoView继承ImageView实现了超大图加载，Demo中演示了如何在Pager加载静态图片和动图，同时也支持各种手势操作。
我在公司的产品上自定义了XPhotoView，在包括聊天列表，动图播放，还有高清大图查看的功能上已经验证了它的稳定和高效，平时的开发中可以直接使用。

### 超大图片加载和局部解析
对于普通的图片，我们加载的思路很简单就是压缩大小，用Options来获得大小然后和当前屏幕大小进行比较，然后以一定的值压缩。但是这样带来的问题是，压缩后的图片会丢失细节，如果是小泽...呸，如果是清明上河图压缩到屏幕大小，放大后怕是人都看不见。而整张图片加载肯定是行不通的，内存绝对立马爆。
解决方案很简单，我们只加载图片的局部区域，这部分区域适配屏幕大小，配合手势移动的时候更新显示对应的区域就可以了。
Android提供了[BitmapRegionDecoder](https://developer.android.com/reference/android/graphics/BitmapRegionDecoder.html)来进行图片的局部解析，这是XPhotoView的主要思路。
剩下的就是如何高效的加载的问题了，如何设计代码逻辑，让它能够快速的响应手势动作呢。

### 局部解析的代码逻辑

#### 代码结构
XPhotoView的代码概要如下所示，
````
--|--|--XPhotoView
  |  |
  |  |--PhotoViewAttacher
  |--GestureManager
````
大体可以分为两部分，XPhotoView和PhotoViewAttacher负责图片的加载和解析，GestureManager负责手势的判断和响应。整个库对外暴露的只是XPhotoView的几个public方法用来setImage和相关的Listener，还有是否是Gif的参数。
Attacher本身只负责图片的拆分解析和渲染过程，同时Bitmap也是保存在Attacher中。Attacher和XPhotoView之间通过Interface互相调用，以此隔离。

#### Attacher 的解析过程
我们暂时忽略Gif的部分，先描述一下Attacher的思路。
Attacher有一个内部子类BitmapUnit和BitmapGridStrategy，初始图片会被BitmapRegionDecoder切割为 N*M 的网格，然后存储在BitmapUnit[N][M]二维数组 mGrids 中。

![image.png](http://upload-images.jianshu.io/upload_images/4691622-e8f259fdb4de0c3a.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


以清明上河图为例，图中高亮的线条把图片分割为三部分，就是说我们用 BitmapUnit[1][3] 来存储这张图片。这么做的原因是，当我们放大图片来查看的时候，只需要解析单个格子以及它相邻格子里的图片。
当然在我们以适配屏幕的条件下查看全图时，是经过mSampleSize比例压缩的，也就是说在mGrids中的Bitmap是压缩过后的占小内存的位图，不用担心OOM的问题。
````
    /** 当前图片的的采样率 */
    private int mSampleSize = 0;
    /***
     * View Rect
     * View 坐标系*/
    private Rect mViewRect = new Rect();

    /** 原图 Rect
     *  Bitmap 坐标系 */
    private Rect mImageRect = new Rect();

    /**
     * 实际展示的 Bitmap 大小
     * Bitmap 坐标系 */
    private RectF mShowBitmapRect = new RectF();
    /**
     * view 相对 Show Bitmap 的坐标
     * Bitmap 坐标系 */
    private Rect mViewBitmapRect = new Rect();
````
以上是Attacher中的关键变量，整个解析和渲染的过程基于这四个Rect的坐标。

现在我们开始整个流程。

##### 初始化

````
/**
 * @param bitmap 设置 bitmap
 * @param cache 是否cache
 */
void setBitmap(Bitmap bitmap, boolean cache);

/**
 * @param is 设置输入流
 * @param config config
 */
void setInputStream(InputStream is, Bitmap.Config config);
````

这两个是Attacher定义的对外接口，它只允许两种方式来设置图片，不管是哪个方式，都会转换为InputStream对象mDecodeInputStream，来作为BitmapRegionDecoder的来源。
若以setBitmap()方法初始化的话，会多设置一个mSrcBitmap，当进行局部解析时就不会通过BitmapRegionDecoder来解析，而是会直接从mSrcBitmap中createBitmap对应的区域出来。这种方式的前提是默认不会出现OOM，毕竟已经可以整个Bitmap作为参数传进来了，但是不能保证在后面createBitmap时不会OOM，所以不提倡用这个方法来初始化。

在调用这两个方法任何一个之后，都会调用initialize()来初始化需要的线程和Handler，
````
/** 初始化所需参数和线程*/
private synchronized void initialize(Bitmap.Config config)
````
然后我们来到setBitmapDecoder(final InputStream is)方法，此时我们开始真正的拆图和解析。这个方法是所有的起点，而且只会也只应该走一次。
它会把mInstanceDecoderRunnable丢给handler然后开始运行，在解析完成后通过回调告知上层解析完毕，可以进行关闭进度条之类的操作。

##### 获取图片初始显示参数

此时我们会调用这个方法
````
private void initiateViewRect(int viewWidth, int viewHeight)
````
但是第一次调用的时候是在onDraw之前，在setImage之后，此时我们并不知道具体的Canvas的大小，因此没法确定缩放比例，还有其他的Rect所需要的初始化的具体值。因此此时mViewRect的值都还是0，作为参数传进来后经过校验是无效值，则会退出此次的方法。
这时我们来看看draw()方法，
````
@Override
public boolean draw(@NonNull Canvas canvas, int width, int height) {
    if (isNotAvailable()) {
        return false;
    }

    if (mSrcBitmap != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        int mw = canvas.getMaximumBitmapWidth();
        int mh = canvas.getMaximumBitmapHeight();

        /**
         * 如果图片太大，直接使用bitmap 会占用很大内存，所以建议缓存为文件再显示
         */
        if (mSrcBitmap.getHeight() > mh || mSrcBitmap.getWidth() > mw) {
            //TODO
        }
    }

    /**
     * 更新视图或者画出图片
     */
    return !checkOrUpdateViewRect(width, height) && mBitmapGrid.drawVisibleGrid(canvas);
}
````
在XPhotoView调用draw()方法后，最后会进行有效性检查，就是checkOrUpdateViewRect()，这时会把真正的视图的大小作为参数传给 initiateViewRect()，然后再真正的进行参数的初始化。
是的，这里我们用延迟的方式来获取到真正的视图大小，虽然代码不容易理解，但是稳定性提高了。

接下来我们要初始化几个参数，
````
mShowBitmapRect, mViewBitmapRect, mSampleSize
````

图片的初始化显示方式有几种，按Android的定义有FIT_CENTER,CENTER_CROP等，这里我们默认用 CENTER_CROP 的方式，而且显示图片的起始部分，横图从左边开始，竖图从最上面开始。
这里需要关注的关键代码是，
````
    private void initiateViewRect(int viewWidth, int viewHeight) {
      ····
      /** 以 view 宽/长比例和 image 宽/长比例做比较
         *  iW/iH < vW/vH : 左右留空，取高比值
         *  iW/iH > vW/vH : 上下留空，取宽比值 */
        float ratio = (imgWidth/imgHeight < viewWidth/viewHeight) ? (imgHeight * 1.0f / viewHeight) : (imgWidth * 1.0f / viewWidth);

        mShowBitmapRect.set(0, 0, (int) (imgWidth / ratio), (int) (imgHeight / ratio));

        /** 保存初始大小 */
        mShowBitmapRect.round(mInitiatedShowBitmapRect);

        /** 取缩小到适配view时的bitmap的起始位置 */
        int left = (int) ((mShowBitmapRect.width() - mViewRect.width()) / 2);
        int top = (int) ((mShowBitmapRect.height() - mViewRect.height()) / 2);

        left = mShowBitmapRect.width() < mViewRect.width() ? left : 0;
        int right = left + mViewRect.width();
        top = mShowBitmapRect.height() < mViewRect.height() ? top : 0;
        int bottom = top + mViewRect.height();

        mViewBitmapRect.set(left, top, right, bottom);
      ····
    }
````
我们通过比较View和Image的高/宽比例，确定图片是横图还是竖图，
以横图为例子，此时我们需要将它上下撑大到刚好铺满屏幕，那么就得以View的高度和Image的高度来算出压缩值 ratio，用它来算出压缩后的图片的实际大小，保存在 mShowBitmapRect中。
完成这一步后，接下来计算mViewBitmapRect。还记得我们的设定是显示图片的起始部位么，
````
/** 取缩小到适配view时的bitmap的起始位置 */
int left = (int) ((mShowBitmapRect.width() - mViewRect.width()) / 2);
int top = (int) ((mShowBitmapRect.height() - mViewRect.height()) / 2);

left = mShowBitmapRect.width() < mViewRect.width() ? left : 0;
int right = left + mViewRect.width();
top = mShowBitmapRect.height() < mViewRect.height() ? top : 0;
int bottom = top + mViewRect.height();

mViewBitmapRect.set(left, top, right, bottom);
````
这部分代码，既保证了超大图在缩放后从起始位置开始，也保证了普通图片缩放后不满屏的情况下居中显示，大家可以琢磨琢磨。

要留意initiateViewRect这个方法，它不仅在初始化的时候调用，后面每次的缩放，都需要调用它来更新mShowBitmapRect，因为每次的缩放都会让实际显示的图片大小发生改变。

##### 绘制流程
关注 draw()方法，这里是绘制流程的关键部分，
每次绘制前都会先更新当前的各个Rect对象，以获得对应的显示中的Grid，我们只绘制显示出来的部分Grid单元，
下面的图大致描述在绘制时的情形，
````
* +--+--+--+--+--+
* |XX|XX|11|11|XX|
* +--+--+--+--+--+
* |XX|XX|11|11|XX|
* +--+--+--+--+--+
* |XX|XX|XX|XX|XX|
* +--+--+--+--+--+
````
标记为11的四个格子，表示目前可见的区域，xx的表示不可见区域。
对于可见区域，会结合当前的缩放值，从mBitmapGrid中取出，然后通过XPhotoView传进来的Canvas对象绘制，
对于不可见区域，会回收掉对应的bitmap对象，以节省内存。


### 手势响应
关于手势响应，是比较简单的一个部分，
我们定义了GestureManager，把XPhotoView的事件交给GestureManager的onTouchEvent()处理，
这部分代码相对简单，不做过多解释。

### 兼容动图
动图的显示方式有两种方案，
- 用Movie类来显示
- 托管给Glide的GifDrawable去渲染

#### Movie的方式
这种方式相对简单，
在我们不知道对应的文件或者图片是否是动图的情况下，以正常逻辑设置即可，
设置之后会用Movie类来判断是否是一个有效的GIF图，
之后在draw时，在Gif的情况下会用Movie类来进行渲染

#### Glide的方式
很多项目会用Glide来做图片的下载和显示，
Glide本身会判断图片是否为Gif，当是Gif时会构造一个 GifDrawable 对象，
我们直接把这个GifDrawable对象用 setImageDrawable 的方式设置到XPhotoView，
GifDrawable会接管动图的绘制流程。
注意如果这种情况下动图不动的话，需要在 setImageDrawable 之后调用 GifDrawable 的 start()方法，
````
if(glideDrawable instanceof GifDrawable) {
  holder.photoView.setGif(true);
  holder.photoView.setImageDrawable(glideDrawable);
  ((GifDrawable) glideDrawable).start();
}
````
