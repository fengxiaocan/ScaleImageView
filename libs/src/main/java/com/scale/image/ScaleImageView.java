package com.scale.image;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewParent;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.chrisbanes.photoview.PhotoView;
import com.scale.image.decoder.CompatDecoderFactory;
import com.scale.image.decoder.DecoderFactory;
import com.scale.image.decoder.ImageDecoder;
import com.scale.image.decoder.ImageDecoder2;
import com.scale.image.decoder.ImageRegionDecoder;
import com.scale.image.decoder.SkiaImageDecoder;
import com.scale.image.decoder.SkiaImageRegionDecoder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings("unused")
public class ScaleImageView extends PhotoView implements IScaleImageView{

    protected static final String TAG=ScaleImageView.class.getSimpleName();

    public static final int ORIENTATION_USE_EXIF=-1;
    public static final int ORIENTATION_0=0;
    public static final int ORIENTATION_90=90;
    public static final int ORIENTATION_180=180;
    public static final int ORIENTATION_270=270;

    protected static final List<Integer> VALID_ORIENTATIONS=Arrays.asList(ORIENTATION_0,
                                                                          ORIENTATION_90,
                                                                          ORIENTATION_180,
                                                                          ORIENTATION_270,
                                                                          ORIENTATION_USE_EXIF);

    public static final int ZOOM_FOCUS_FIXED=1;
    public static final int ZOOM_FOCUS_CENTER=2;
    public static final int ZOOM_FOCUS_CENTER_IMMEDIATE=3;

    protected static final List<Integer> VALID_ZOOM_STYLES=Arrays.asList(ZOOM_FOCUS_FIXED,
                                                                         ZOOM_FOCUS_CENTER,
                                                                         ZOOM_FOCUS_CENTER_IMMEDIATE);

    public static final int EASE_OUT_QUAD=1;
    public static final int EASE_IN_OUT_QUAD=2;

    protected static final List<Integer> VALID_EASING_STYLES=Arrays.asList(EASE_IN_OUT_QUAD,EASE_OUT_QUAD);

    public static final int PAN_LIMIT_INSIDE=1;
    public static final int PAN_LIMIT_OUTSIDE=2;
    public static final int PAN_LIMIT_CENTER=3;

    protected static final List<Integer> VALID_PAN_LIMITS=Arrays.asList(PAN_LIMIT_INSIDE,
                                                                        PAN_LIMIT_OUTSIDE,
                                                                        PAN_LIMIT_CENTER);

    public static final int SCALE_TYPE_CENTER_INSIDE=1;
    public static final int SCALE_TYPE_CENTER_CROP=2;
    public static final int SCALE_TYPE_CUSTOM=3;
    public static final int SCALE_TYPE_START=4;

    protected static final List<Integer> VALID_SCALE_TYPES=Arrays.asList(SCALE_TYPE_CENTER_CROP,
                                                                         SCALE_TYPE_CENTER_INSIDE,
                                                                         SCALE_TYPE_CUSTOM,
                                                                         SCALE_TYPE_START);

    public static final int ORIGIN_ANIM=1;
    public static final int ORIGIN_TOUCH=2;
    public static final int ORIGIN_FLING=3;
    public static final int ORIGIN_DOUBLE_TAP_ZOOM=4;

    // Bitmap (preview or full image)
    protected Bitmap bitmap;

    // Whether the bitmap is a preview image
    protected boolean bitmapIsPreview;

    // Specifies if a cache handler is also referencing the bitmap. Do not recycle if so.
    protected boolean bitmapIsCached;

    // Uri of full size image
    protected Uri uri;

    // Sample size used to display the whole image when fully zoomed out
    protected int fullImageSampleSize;

    // Map of zoom level to tile grid
    protected Map<Integer,List<Tile>> tileMap;

    // Overlay tile boundaries and other info
    protected boolean debug=false;

    // Image orientation setting
    protected int orientation = ORIENTATION_USE_EXIF;

    // Max scale allowed (prevent infinite zoom)
    protected float maxScale=2F;

    // Min scale allowed (prevent infinite zoom)
    protected float minScale=minScale();

    // Density to reach before loading higher resolution tiles
    protected int minimumTileDpi=-1;

    // Pan limiting style
    protected int panLimit=PAN_LIMIT_INSIDE;

    // Minimum scale type
    protected int minimumScaleType=SCALE_TYPE_CENTER_INSIDE;

    // overrides for the dimensions of the generated tiles
    public static final int TILE_SIZE_AUTO=Integer.MAX_VALUE;
    protected int maxTileWidth=TILE_SIZE_AUTO;
    protected int maxTileHeight=TILE_SIZE_AUTO;

    // An executor service for loading of images
    protected Executor executor=AsyncTask.THREAD_POOL_EXECUTOR;

    // Whether tiles should be loaded while gestures and animations are still in progress
    protected boolean eagerLoadingEnabled=true;

    // Gesture detection settings
    protected boolean panEnabled=true;
    protected boolean zoomEnabled=true;
    protected boolean quickScaleEnabled=true;

    // Double tap zoom behaviour
    protected float doubleTapZoomScale=1F;
    protected int doubleTapZoomStyle=ZOOM_FOCUS_FIXED;
    protected int doubleTapZoomDuration=500;

    // Current scale and scale at start of zoom
    protected float scale;
    protected float scaleStart;

    // Screen coordinate of top-left corner of source image
    protected PointF vTranslate;
    protected PointF vTranslateStart;
    protected PointF vTranslateBefore;

    // Source coordinate to center on, used when new position is set externally before view is ready
    protected Float pendingScale;
    protected PointF sPendingCenter;
    protected PointF sRequestedCenter;

    // Source image dimensions and orientation - dimensions relate to the unrotated image
    protected int sWidth;
    protected int sHeight;
    protected int sOrientation;
    protected Rect sRegion;
    protected Rect pRegion;

    // Is two-finger zooming in progress
    protected boolean isZooming;
    // Is one-finger panning in progress
    protected boolean isPanning;
    // Is quick-scale gesture in progress
    protected boolean isQuickScaling;
    // Max touches used in current gesture
    protected int maxTouchCount;

    // Fling detector
    protected GestureDetector detector;
    protected GestureDetector singleDetector;

    // Tile and image decoding
    protected ImageRegionDecoder decoder;
    protected final ReadWriteLock decoderLock=new ReentrantReadWriteLock(true);
    protected DecoderFactory<? extends ImageDecoder> bitmapDecoderFactory=new CompatDecoderFactory<ImageDecoder>(
            SkiaImageDecoder.class);
    protected DecoderFactory<? extends ImageRegionDecoder> regionDecoderFactory=
            new CompatDecoderFactory<ImageRegionDecoder>(SkiaImageRegionDecoder.class);

    // Debug values
    protected PointF vCenterStart;
    protected float vDistStart;

    // Current quickscale state
    protected final float quickScaleThreshold;
    protected float quickScaleLastDistance;
    protected boolean quickScaleMoved;
    protected PointF quickScaleVLastPoint;
    protected PointF quickScaleSCenter;
    protected PointF quickScaleVStart;

    // Scale and center animation tracking
    protected Anim anim;

    // Whether a ready notification has been sent to subclasses
    protected boolean readySent;
    // Whether a base layer loaded notification has been sent to subclasses
    protected boolean imageLoadedSent;

    // Event listener
    protected OnImageEventListener onImageEventListener;

    // Scale and center listener
    protected OnStateChangedListener onStateChangedListener;

    // Long click listener
//    protected OnLongClickListener onLongClickListener;

    // Long click handler
    protected final Handler handler;
    protected static final int MESSAGE_LONG_CLICK=1;

    // Paint objects created once and reused for efficiency
    protected Paint bitmapPaint;
    protected Paint debugTextPaint;
    protected Paint debugLinePaint;
    protected Paint tileBgPaint;

    // Volatile fields used to reduce object creation
    protected ScaleAndTranslate satTemp;
    protected Matrix matrix;
    protected RectF sRect;
    protected final float[] srcArray=new float[8];
    protected final float[] dstArray=new float[8];

    //The logical density of the display
    protected final float density;

    protected boolean isCallSuperOnDraw;

    protected GestureDetector.OnDoubleTapListener onDoubleTapListener;

    // A global preference for bitmap format, available to decoder classes that respect it
    protected static Bitmap.Config preferredBitmapConfig;


    public ScaleImageView(Context context,@Nullable AttributeSet attr,int defStyleAttr) {
        super(context, attr, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        setMinimumDpi(160);
        setDoubleTapZoomDpi(160);
        setMinimumTileDpi(320);
        setGestureDetector(context);
        this.handler = new Handler(new Handler.Callback() {
            public boolean handleMessage(Message message) {
                if (message.what == MESSAGE_LONG_CLICK) {
                    maxTouchCount = 0;
                    performLongClick();
                }
                return true;
            }
        });
        // Handle XML attributes
        if (attr != null) {
            TypedArray typedAttr = getContext().obtainStyledAttributes(attr, R.styleable.ScaleImageView);
            if (typedAttr.hasValue(R.styleable.ScaleImageView_assetName)) {
                String assetName = typedAttr.getString(R.styleable.ScaleImageView_assetName);
                if (assetName != null && assetName.length() > 0) {
                    setImage(ImageSource.asset(assetName).tilingEnabled());
                }
            }
            if (typedAttr.hasValue(R.styleable.ScaleImageView_src)) {
                int resId = typedAttr.getResourceId(R.styleable.ScaleImageView_src, 0);
                if (resId > 0) {
                    setImage(ImageSource.resource(resId).tilingEnabled());
                }
            }
            if (typedAttr.hasValue(R.styleable.ScaleImageView_panEnabled)) {
                setPanEnabled(typedAttr.getBoolean(R.styleable.ScaleImageView_panEnabled, true));
            }
            if (typedAttr.hasValue(R.styleable.ScaleImageView_zoomEnabled)) {
                setZoomEnabled(typedAttr.getBoolean(R.styleable.ScaleImageView_zoomEnabled, true));
            }
            if (typedAttr.hasValue(R.styleable.ScaleImageView_quickScaleEnabled)) {
                setQuickScaleEnabled(typedAttr.getBoolean(R.styleable.ScaleImageView_quickScaleEnabled, true));
            }
            if (typedAttr.hasValue(R.styleable.ScaleImageView_tileBackgroundColor)) {
                setTileBackgroundColor(typedAttr.getColor(R.styleable.ScaleImageView_tileBackgroundColor,
                        Color.argb(0, 0, 0, 0)));
            }
            typedAttr.recycle();
        }

        quickScaleThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                20,
                context.getResources().getDisplayMetrics());

    }

    public ScaleImageView(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public ScaleImageView(Context context) {
        this(context, null);
    }


    public static Bitmap.Config getPreferredBitmapConfig() {
        return preferredBitmapConfig;
    }

    public static void setPreferredBitmapConfig(Bitmap.Config preferredBitmapConfig) {
        ScaleImageView.preferredBitmapConfig = preferredBitmapConfig;
    }


    public final void setOrientation(int orientation) {
        if (!VALID_ORIENTATIONS.contains(orientation)) {
            throw new IllegalArgumentException("Invalid orientation: " + orientation);
        }
        this.orientation = orientation;
        reset(false);
        invalidate();
        requestLayout();
    }

    public final void setImage(@NonNull ImageSource imageSource) {
        setImage(imageSource, null, null);
    }

    public final void setImage(@NonNull ImageSource imageSource, ImageViewState state) {
        setImage(imageSource, null, state);
    }

    public final void setImage(@NonNull ImageSource imageSource, ImageSource previewSource) {
        setImage(imageSource, previewSource, null);
    }

    public final void setImage(@NonNull ImageSource imageSource, ImageSource previewSource, ImageViewState state) {
        //noinspection ConstantConditions
        if (imageSource == null) {
            throw new NullPointerException("imageSource must not be null");
        }

        reset(true);
        if (state != null) {
            restoreState(state);
        }

        if (previewSource != null) {
            if (imageSource.getBitmap() != null) {
                throw new IllegalArgumentException(
                        "Preview image cannot be used when a bitmap is provided for the main image");
            }
            if (imageSource.getSWidth() <= 0 || imageSource.getSHeight() <= 0) {
                throw new IllegalArgumentException(
                        "Preview image cannot be used unless dimensions are provided for the main image");
            }
            this.sWidth = imageSource.getSWidth();
            this.sHeight = imageSource.getSHeight();
            this.pRegion = previewSource.getSRegion();
            if (previewSource.getBitmap() != null) {
                this.bitmapIsCached = previewSource.isCached();
                onPreviewLoaded(previewSource.getBitmap());
            } else {
                Uri uri = previewSource.getUri();
                if (uri == null && previewSource.getResource() != null) {
                    uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getContext().getPackageName() + "/" +
                            previewSource.getResource());
                }
                BitmapLoadTask task = new BitmapLoadTask(this, getContext(), bitmapDecoderFactory, uri, true);
                execute(task);
            }
        }

        if (imageSource.getBitmap() != null && imageSource.getSRegion() != null) {
            onImageLoaded(Bitmap.createBitmap(imageSource.getBitmap(),
                    imageSource.getSRegion().left,
                    imageSource.getSRegion().top,
                    imageSource.getSRegion().width(),
                    imageSource.getSRegion().height()), ORIENTATION_0, false);
        } else if (imageSource.getBitmap() != null) {
            onImageLoaded(imageSource.getBitmap(), ORIENTATION_0, imageSource.isCached());
        } else {
            sRegion = imageSource.getSRegion();
            uri = imageSource.getUri();
            if (uri == null && imageSource.getResource() != null) {
                uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getContext().getPackageName() + "/" +
                        imageSource.getResource());
            }
            if (imageSource.getTile() || sRegion != null) {
                // Load the bitmap using tile decoding.
                TilesInitTask task = new TilesInitTask(this, getContext(), regionDecoderFactory, uri);
                execute(task);
            } else {
                // Load the bitmap as a single image.
                BitmapLoadTask task = new BitmapLoadTask(this, getContext(), bitmapDecoderFactory, uri, false);
                execute(task);
            }
        }
    }

    protected void reset(boolean newImage) {
        debug("reset newImage=" + newImage);
        scale = 0f;
        scaleStart = 0f;
        vTranslate = null;
        vTranslateStart = null;
        vTranslateBefore = null;
        pendingScale = 0f;
        sPendingCenter = null;
        sRequestedCenter = null;
        isZooming = false;
        isPanning = false;
        isQuickScaling = false;
        maxTouchCount = 0;
        fullImageSampleSize = 0;
        vCenterStart = null;
        vDistStart = 0;
        quickScaleLastDistance = 0f;
        quickScaleMoved = false;
        quickScaleSCenter = null;
        quickScaleVLastPoint = null;
        quickScaleVStart = null;
        anim = null;
        satTemp = null;
        matrix = null;
        sRect = null;
        if (newImage) {
            uri = null;
            decoderLock.writeLock().lock();
            try {
                if (decoder != null) {
                    decoder.recycle();
                    decoder = null;
                }
            } finally {
                decoderLock.writeLock().unlock();
            }
            if (bitmap != null && !bitmapIsCached) {
                bitmap.recycle();
            }
            if (bitmap != null && bitmapIsCached && onImageEventListener != null) {
                onImageEventListener.onPreviewReleased();
            }
            sWidth=0;
            sHeight=0;
            sOrientation=0;
            sRegion=null;
            pRegion=null;
            readySent=false;
            imageLoadedSent=false;
            bitmap=null;
            bitmapIsPreview=false;
            bitmapIsCached=false;
        }
        if(tileMap!=null){
            for(Map.Entry<Integer,List<Tile>> tileMapEntry: tileMap.entrySet()){
                for(Tile tile: tileMapEntry.getValue()){
                    tile.visible=false;
                    if(tile.bitmap!=null){
                        tile.bitmap.recycle();
                        tile.bitmap = null;
                    }
                }
            }
            tileMap = null;
        }
        setGestureDetector(getContext());
    }

    protected void setGestureDetector(final Context context) {
        if (detector == null) {
            this.detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (panEnabled && readySent && vTranslate != null && e1 != null && e2 != null && (Math.abs(e1.getX() - e2.getX()) > 50 ||
                            Math.abs(e1.getY() - e2.getY()) > 50) &&
                            (Math.abs(velocityX) > 500 || Math.abs(velocityY) > 500) && !isZooming) {
                        PointF vTranslateEnd = new PointF(vTranslate.x + (velocityX * 0.25f), vTranslate.y + (velocityY * 0.25f));
                        float sCenterXEnd = ((getWidth() / 2) - vTranslateEnd.x) / scale;
                        float sCenterYEnd = ((getHeight() / 2) - vTranslateEnd.y) / scale;
                        new AnimationBuilder(new PointF(sCenterXEnd, sCenterYEnd)).withEasing(EASE_OUT_QUAD)
                                .withPanLimited(false)
                                .withOrigin(ORIGIN_FLING)
                                .start();
                        return true;
                    }
                    return super.onFling(e1, e2, velocityX, velocityY);
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e){
                    performClick();
                    final float x=e.getX(), y=e.getY();
                    if(mViewTapListener!=null){
                        mViewTapListener.onViewTap(ScaleImageView.this,x,y);
                    }
                    if(onDoubleTapListener!=null){
                        onDoubleTapListener.onSingleTapConfirmed(e);
                    }
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e){

                    if(onDoubleTapListener!=null){
                        onDoubleTapListener.onDoubleTap(e);
                    }
                    if(zoomEnabled&&readySent&&vTranslate!=null){
                        // Hacky solution for #15 - after a double tap the GestureDetector gets in a state
                        // where the next fling is ignored, so here we replace it with a new one.
//                    setGestureDetector(context);
                        if(quickScaleEnabled){
                            // Store quick scale params. This will become either a double tap zoom or a
                            // quick scale depending on whether the user swipes.
                            vCenterStart=new PointF(e.getX(),e.getY());
                            vTranslateStart=new PointF(vTranslate.x,vTranslate.y);
                            scaleStart=scale;
                            isQuickScaling=true;
                            isZooming=true;
                            quickScaleLastDistance=-1F;
                            quickScaleSCenter=viewToSourceCoord(vCenterStart);
                            quickScaleVStart=new PointF(e.getX(),e.getY());
                            quickScaleVLastPoint=new PointF(quickScaleSCenter.x,quickScaleSCenter.y);
                            quickScaleMoved=false;
                            // We need to get events in onTouchEvent after this.
                            return false;
                        } else{
                            // Start double tap zoom animation.
                            doubleTapZoom(viewToSourceCoord(new PointF(e.getX(),e.getY())),
                                          new PointF(e.getX(),e.getY()));
                            return true;
                        }
                    }
                    return super.onDoubleTapEvent(e);
                }

                @Override
                public boolean onDoubleTapEvent(MotionEvent e){
                    if(onDoubleTapListener!=null){
                        return onDoubleTapListener.onDoubleTapEvent(e);
                    }
                    return super.onDoubleTapEvent(e);
                }

            });
        }
        if(singleDetector==null){
            singleDetector=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e){
                    performClick();
                    final float x=e.getX(), y=e.getY();
                    if(mViewTapListener!=null){
                        mViewTapListener.onViewTap(ScaleImageView.this,x,y);
                    }
                    if(onDoubleTapListener!=null){
                        return onDoubleTapListener.onSingleTapConfirmed(e);
                    }
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e){
                    if(onDoubleTapListener!=null){
                        return onDoubleTapListener.onDoubleTap(e);
                    }
                    return super.onDoubleTap(e);
                }

                @Override
                public boolean onDoubleTapEvent(MotionEvent e) {
                    if (onDoubleTapListener != null) {
                        return onDoubleTapListener.onDoubleTapEvent(e);
                    }
                    return super.onDoubleTapEvent(e);
                }
            });
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        debug("onSizeChanged %dx%d -> %dx%d", oldw, oldh, w, h);
        PointF sCenter = getCenter();
        if (readySent && sCenter != null) {
            this.anim = null;
            this.pendingScale = scale;
            this.sPendingCenter = sCenter;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        boolean resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
        boolean resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
        int width = parentWidth;
        int height = parentHeight;
        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth();
                height = sHeight();
            } else if (resizeHeight) {
                height = (int) ((((double) sHeight() / (double) sWidth()) * width));
            } else if (resizeWidth) {
                width = (int) ((((double) sWidth() / (double) sHeight()) * height));
            }
        }
        width = Math.max(width, getSuggestedMinimumWidth());
        height = Math.max(height, getSuggestedMinimumHeight());
        setMeasuredDimension(width, height);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // During non-interruptible anims, ignore all touch events
        if (anim != null && !anim.interruptible) {
            requestDisallowInterceptTouchEvent(true);
            return true;
        } else {
            if (anim != null && anim.listener != null) {
                try {
                    anim.listener.onInterruptedByUser();
                } catch (Exception e) {
                    Log.w(TAG, "Error thrown by animation listener", e);
                }
            }
            anim = null;
        }

        // Abort if not ready
        if (vTranslate == null) {
            if (singleDetector != null) {
                singleDetector.onTouchEvent(event);
            }
            return true;
        }
        // Detect flings, taps and double taps
        if (!isQuickScaling && (detector == null || detector.onTouchEvent(event))) {
            isZooming = false;
            isPanning = false;
            maxTouchCount = 0;
            return true;
        }

        if (vTranslateStart == null) {
            vTranslateStart = new PointF(0, 0);
        }
        if (vTranslateBefore == null) {
            vTranslateBefore = new PointF(0, 0);
        }
        if (vCenterStart == null) {
            vCenterStart = new PointF(0, 0);
        }

        // Store current values so we can send an event if they change
        float scaleBefore = scale;
        vTranslateBefore.set(vTranslate);

        boolean handled = onTouchEventInternal(event);
        sendStateChanged(scaleBefore, vTranslateBefore, ORIGIN_TOUCH);
        return handled || super.onTouchEvent(event);
    }

    @SuppressWarnings("deprecation")
    protected boolean onTouchEventInternal(@NonNull MotionEvent event) {
        int touchCount = event.getPointerCount();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_1_DOWN:
            case MotionEvent.ACTION_POINTER_2_DOWN:
                anim = null;
                requestDisallowInterceptTouchEvent(true);
                maxTouchCount = Math.max(maxTouchCount, touchCount);
                if (touchCount >= 2) {
                    if (zoomEnabled) {
                        // Start pinch to zoom. Calculate distance between touch points and center point of the pinch.
                        float distance = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                        scaleStart = scale;
                        vDistStart = distance;
                        vTranslateStart.set(vTranslate.x, vTranslate.y);
                        vCenterStart.set((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2);
                    } else {
                        // Abort all gestures on second touch
                        maxTouchCount = 0;
                    }
                    // Cancel long click timer
                    handler.removeMessages(MESSAGE_LONG_CLICK);
                } else if(!isQuickScaling){
                    // Start one-finger pan
                    vTranslateStart.set(vTranslate.x,vTranslate.y);
                    vCenterStart.set(event.getX(),event.getY());

                    // Start long click timer
                    handler.sendEmptyMessageDelayed(MESSAGE_LONG_CLICK,600);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                boolean consumed=false;
                if(maxTouchCount>0){
                    if(touchCount >= 2){
                        // Calculate new distance between touch points, to scale and pan relative to start values.
                        float vDistEnd=distance(event.getX(0),event.getX(1),event.getY(0),event.getY(1));
                        float vCenterEndX=(event.getX(0)+event.getX(1))/2;
                        float vCenterEndY=(event.getY(0)+event.getY(1))/2;

                        if(zoomEnabled&&(distance(vCenterStart.x,vCenterEndX,vCenterStart.y,vCenterEndY)>5||Math.abs(
                                vDistEnd-vDistStart)>5||isPanning))
                        {
                            isZooming=true;
                            isPanning=true;
                            consumed=true;

                            double previousScale=scale;
                            scale=Math.min(maxScale,(vDistEnd/vDistStart)*scaleStart);

                            if(scale<=minScale()){
                                // Minimum scale reached so don't pan. Adjust start settings so any expand will zoom in.
                                vDistStart=vDistEnd;
                                scaleStart=minScale();
                                vCenterStart.set(vCenterEndX,vCenterEndY);
                                vTranslateStart.set(vTranslate);
                            } else if(panEnabled){
                                // Translate to place the source image coordinate that was at the center of the pinch at the start
                                // at the center of the pinch now, to give simultaneous pan + zoom.
                                float vLeftStart=vCenterStart.x-vTranslateStart.x;
                                float vTopStart=vCenterStart.y-vTranslateStart.y;
                                float vLeftNow=vLeftStart*(scale/scaleStart);
                                float vTopNow=vTopStart*(scale/scaleStart);
                                vTranslate.x=vCenterEndX-vLeftNow;
                                vTranslate.y=vCenterEndY-vTopNow;
                                if((previousScale*sHeight()<getHeight()&&scale*sHeight() >= getHeight())||
                                   (previousScale*sWidth()<getWidth()&&scale*sWidth() >= getWidth()))
                                {
                                    fitToBounds(true);
                                    vCenterStart.set(vCenterEndX,vCenterEndY);
                                    vTranslateStart.set(vTranslate);
                                    scaleStart=scale;
                                    vDistStart=vDistEnd;
                                }
                            } else if(sRequestedCenter!=null){
                                // With a center specified from code, zoom around that point.
                                vTranslate.x=(getWidth()/2)-(scale*sRequestedCenter.x);
                                vTranslate.y=(getHeight()/2)-(scale*sRequestedCenter.y);
                            } else{
                                // With no requested center, scale around the image center.
                                vTranslate.x=(getWidth()/2)-(scale*(sWidth()/2));
                                vTranslate.y=(getHeight()/2)-(scale*(sHeight()/2));
                            }

                            fitToBounds(true);
                            refreshRequiredTiles(eagerLoadingEnabled);
                        }
                    } else if(isQuickScaling){
                        // One finger zoom
                        // Stole Google's Magical Formula™ to make sure it feels the exact same
                        float dist=Math.abs(quickScaleVStart.y-event.getY())*2+quickScaleThreshold;

                        if(quickScaleLastDistance==-1f){
                            quickScaleLastDistance=dist;
                        }
                        boolean isUpwards=event.getY()>quickScaleVLastPoint.y;
                        quickScaleVLastPoint.set(0,event.getY());

                        float spanDiff=Math.abs(1-(dist/quickScaleLastDistance))*0.5f;

                        if(spanDiff>0.03f||quickScaleMoved){
                            quickScaleMoved=true;

                            float multiplier=1;
                            if(quickScaleLastDistance>0){
                                multiplier=isUpwards ? (1+spanDiff) : (1-spanDiff);
                            }

                            double previousScale=scale;
                            scale=Math.max(minScale(),Math.min(maxScale,scale*multiplier));

                            if(panEnabled){
                                float vLeftStart=vCenterStart.x-vTranslateStart.x;
                                float vTopStart=vCenterStart.y-vTranslateStart.y;
                                float vLeftNow=vLeftStart*(scale/scaleStart);
                                float vTopNow=vTopStart*(scale/scaleStart);
                                vTranslate.x=vCenterStart.x-vLeftNow;
                                vTranslate.y=vCenterStart.y-vTopNow;
                                if((previousScale*sHeight()<getHeight()&&scale*sHeight() >= getHeight())||
                                   (previousScale*sWidth()<getWidth()&&scale*sWidth() >= getWidth()))
                                {
                                    fitToBounds(true);
                                    vCenterStart.set(sourceToViewCoord(quickScaleSCenter));
                                    vTranslateStart.set(vTranslate);
                                    scaleStart=scale;
                                    dist=0;
                                }
                            } else if(sRequestedCenter!=null){
                                // With a center specified from code, zoom around that point.
                                vTranslate.x=(getWidth()/2)-(scale*sRequestedCenter.x);
                                vTranslate.y=(getHeight()/2)-(scale*sRequestedCenter.y);
                            } else{
                                // With no requested center, scale around the image center.
                                vTranslate.x=(getWidth()/2)-(scale*(sWidth()/2));
                                vTranslate.y=(getHeight()/2)-(scale*(sHeight()/2));
                            }
                        }

                        quickScaleLastDistance=dist;

                        fitToBounds(true);
                        refreshRequiredTiles(eagerLoadingEnabled);

                        consumed=true;
                    } else if(!isZooming){
                        // One finger pan - translate the image. We do this calculation even with pan disabled so click
                        // and long click behaviour is preserved.
                        float dx=Math.abs(event.getX()-vCenterStart.x);
                        float dy=Math.abs(event.getY()-vCenterStart.y);

                        //On the Samsung S6 long click event does not work, because the dx > 5 usually true
                        float offset=density*5;
                        if(dx>offset||dy>offset||isPanning){
                            consumed=true;
                            vTranslate.x=vTranslateStart.x+(event.getX()-vCenterStart.x);
                            vTranslate.y=vTranslateStart.y+(event.getY()-vCenterStart.y);

                            float lastX=vTranslate.x;
                            float lastY=vTranslate.y;
                            fitToBounds(true);
                            boolean atXEdge=lastX!=vTranslate.x;
                            boolean atYEdge=lastY!=vTranslate.y;
                            boolean edgeXSwipe=atXEdge&&dx>dy&&!isPanning;
                            boolean edgeYSwipe=atYEdge&&dy>dx&&!isPanning;
                            boolean yPan=lastY==vTranslate.y&&dy>offset*3;
                            if(!edgeXSwipe&&!edgeYSwipe&&(!atXEdge||!atYEdge||yPan||isPanning)){
                                isPanning=true;
                            } else if(dx>offset||dy>offset){
                                // Haven't panned the image, and we're at the left or right edge. Switch to page swipe.
                                maxTouchCount=0;
                                handler.removeMessages(MESSAGE_LONG_CLICK);
                                requestDisallowInterceptTouchEvent(false);
                            }
                            if(!panEnabled){
                                vTranslate.x=vTranslateStart.x;
                                vTranslate.y=vTranslateStart.y;
                                requestDisallowInterceptTouchEvent(false);
                            }

                            refreshRequiredTiles(eagerLoadingEnabled);
                        }
                    }
                }
                if(consumed){
                    handler.removeMessages(MESSAGE_LONG_CLICK);
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_2_UP:
                handler.removeMessages(MESSAGE_LONG_CLICK);
                if(isQuickScaling){
                    isQuickScaling=false;
                    if(!quickScaleMoved){
                        doubleTapZoom(quickScaleSCenter,vCenterStart);
                    }
                }
                if(maxTouchCount>0&&(isZooming||isPanning)){
                    if (isZooming && touchCount == 2) {
                        // Convert from zoom to pan with remaining touch
                        isPanning = true;
                        vTranslateStart.set(vTranslate.x, vTranslate.y);
                        if (event.getActionIndex() == 1) {
                            vCenterStart.set(event.getX(0), event.getY(0));
                        } else {
                            vCenterStart.set(event.getX(1), event.getY(1));
                        }
                    }
                    if (touchCount < 3) {
                        // End zooming when only one touch point
                        isZooming = false;
                    }
                    if (touchCount < 2) {
                        // End panning when no touch points
                        isPanning = false;
                        maxTouchCount = 0;
                    }
                    // Trigger load of tiles now required
                    refreshRequiredTiles(true);
                    return true;
                }
                if (touchCount == 1) {
                    isZooming = false;
                    isPanning = false;
                    maxTouchCount = 0;
                }
                return true;
        }
        return false;
    }

    protected void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    protected void doubleTapZoom(PointF sCenter, PointF vFocus) {
        if (!panEnabled) {
            if (sRequestedCenter != null) {
                // With a center specified from code, zoom around that point.
                sCenter.x = sRequestedCenter.x;
                sCenter.y = sRequestedCenter.y;
            } else {
                // With no requested center, scale around the image center.
                sCenter.x = sWidth() / 2;
                sCenter.y = sHeight() / 2;
            }
        }
        //双击缩放
        float doubleTapZoomScale = Math.min(maxScale, ScaleImageView.this.doubleTapZoomScale);
//        boolean zoomIn=(scale<=doubleTapZoomScale*0.9)||scale==minScale;
        //改!如果已经缩放过了,再次双击则缩小
        boolean zoomIn = scale <= minScale() * 1.2;

        float targetScale = zoomIn ? doubleTapZoomScale : minScale();
        if (doubleTapZoomStyle == ZOOM_FOCUS_CENTER_IMMEDIATE) {
            setScaleAndCenter(targetScale, sCenter);
        } else if (doubleTapZoomStyle == ZOOM_FOCUS_CENTER || !zoomIn || !panEnabled) {
            new AnimationBuilder(targetScale, sCenter).withInterruptible(false)
                    .withDuration(doubleTapZoomDuration)
                    .withOrigin(ORIGIN_DOUBLE_TAP_ZOOM)
                    .start();
        } else if (doubleTapZoomStyle == ZOOM_FOCUS_FIXED) {
            new AnimationBuilder(targetScale, sCenter, vFocus).withInterruptible(false)
                    .withDuration(doubleTapZoomDuration)
                    .withOrigin(ORIGIN_DOUBLE_TAP_ZOOM)
                    .start();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isCallSuperOnDraw) {
            super.onDraw(canvas);
        } else {
            createPaints();

            // If image or view dimensions are not known yet, abort.
            if (sWidth == 0 || sHeight == 0 || getWidth() == 0 || getHeight() == 0) {
                return;
            }

            // When using tiles, on first render with no tile map ready, initialise it and kick off async base image loading.
            if (tileMap == null && decoder != null) {
                initialiseBaseLayer(getMaxBitmapDimensions(canvas));
            }

            // If image has been loaded or supplied as a bitmap, onDraw may be the first time the view has
            // dimensions and therefore the first opportunity to set scale and translate. If this call returns
            // false there is nothing to be drawn so return immediately.
            if(!checkReady()){
                return;
            }

            // Set scale and translate before draw.
            preDraw();

            // If animating scale, calculate current scale and center with easing equations
            if(anim!=null&&anim.vFocusStart!=null){
                // Store current values so we can send an event if they change
                float scaleBefore=scale;
                if(vTranslateBefore==null){ vTranslateBefore=new PointF(0,0); }
                vTranslateBefore.set(vTranslate);

                long scaleElapsed=System.currentTimeMillis()-anim.time;
                boolean finished=scaleElapsed>anim.duration;
                scaleElapsed=Math.min(scaleElapsed,anim.duration);
                scale=ease(anim.easing,scaleElapsed,anim.scaleStart,anim.scaleEnd-anim.scaleStart,anim.duration);

                // Apply required animation to the focal point
                float vFocusNowX=ease(anim.easing,
                                      scaleElapsed,
                                      anim.vFocusStart.x,
                                      anim.vFocusEnd.x-anim.vFocusStart.x,
                                      anim.duration);
                float vFocusNowY=ease(anim.easing,
                                      scaleElapsed,
                                      anim.vFocusStart.y,
                                      anim.vFocusEnd.y-anim.vFocusStart.y,
                                      anim.duration);
                // Find out where the focal point is at this scale and adjust its position to follow the animation path
                vTranslate.x-=sourceToViewX(anim.sCenterEnd.x)-vFocusNowX;
                vTranslate.y-=sourceToViewY(anim.sCenterEnd.y)-vFocusNowY;

                // For translate anims, showing the image non-centered is never allowed, for scaling anims it is during the animation.
                fitToBounds(finished||(anim.scaleStart==anim.scaleEnd));
                sendStateChanged(scaleBefore,vTranslateBefore,anim.origin);
                refreshRequiredTiles(finished);
                if(finished){
                    if(anim.listener!=null){
                        try{
                            anim.listener.onComplete();
                        } catch(Exception e){
                            Log.w(TAG,"Error thrown by animation listener",e);
                        }
                    }
                    anim=null;
                }
                invalidate();
            }

            if(tileMap!=null&&isBaseLayerReady()){

                // Optimum sample size for current scale
                int sampleSize=Math.min(fullImageSampleSize,calculateInSampleSize(scale));

                // First check for missing tiles - if there are any we need the base layer underneath to avoid gaps
                boolean hasMissingTiles=false;
                for(Map.Entry<Integer,List<Tile>> tileMapEntry: tileMap.entrySet()){
                    if(tileMapEntry.getKey()==sampleSize){
                        for(Tile tile: tileMapEntry.getValue()){
                            if(tile.visible&&(tile.loading||tile.bitmap==null)){
                                hasMissingTiles=true;
                            }
                        }
                    }
                }

                // Render all loaded tiles. LinkedHashMap used for bottom up rendering - lower res tiles underneath.
                for(Map.Entry<Integer,List<Tile>> tileMapEntry: tileMap.entrySet()){
                    if(tileMapEntry.getKey()==sampleSize||hasMissingTiles){
                        for(Tile tile: tileMapEntry.getValue()){
                            sourceToViewRect(tile.sRect,tile.vRect);
                            if(!tile.loading&&tile.bitmap!=null){
                                if(tileBgPaint!=null){
                                    canvas.drawRect(tile.vRect,tileBgPaint);
                                }
                                if(matrix==null){ matrix=new Matrix(); }
                                matrix.reset();
                                setMatrixArray(srcArray,
                                               0,
                                               0,
                                               tile.bitmap.getWidth(),
                                               0,
                                               tile.bitmap.getWidth(),
                                               tile.bitmap.getHeight(),
                                               0,
                                               tile.bitmap.getHeight());
                                if(getRequiredRotation()==ORIENTATION_0){
                                    setMatrixArray(dstArray,
                                                   tile.vRect.left,
                                                   tile.vRect.top,
                                                   tile.vRect.right,
                                                   tile.vRect.top,
                                                   tile.vRect.right,
                                                   tile.vRect.bottom,
                                                   tile.vRect.left,
                                                   tile.vRect.bottom);
                                } else if(getRequiredRotation()==ORIENTATION_90){
                                    setMatrixArray(dstArray,
                                                   tile.vRect.right,
                                                   tile.vRect.top,
                                                   tile.vRect.right,
                                                   tile.vRect.bottom,
                                                   tile.vRect.left,
                                                   tile.vRect.bottom,
                                                   tile.vRect.left,
                                                   tile.vRect.top);
                                } else if(getRequiredRotation()==ORIENTATION_180){
                                    setMatrixArray(dstArray,
                                                   tile.vRect.right,
                                                   tile.vRect.bottom,
                                                   tile.vRect.left,
                                                   tile.vRect.bottom,
                                                   tile.vRect.left,
                                                   tile.vRect.top,
                                                   tile.vRect.right,
                                                   tile.vRect.top);
                                } else if(getRequiredRotation()==ORIENTATION_270){
                                    setMatrixArray(dstArray,
                                                   tile.vRect.left,
                                                   tile.vRect.bottom,
                                                   tile.vRect.left,
                                                   tile.vRect.top,
                                                   tile.vRect.right,
                                                   tile.vRect.top,
                                                   tile.vRect.right,
                                                   tile.vRect.bottom);
                                }
                                matrix.setPolyToPoly(srcArray,0,dstArray,0,4);
                                canvas.drawBitmap(tile.bitmap,matrix,bitmapPaint);
                                if(debug){
                                    canvas.drawRect(tile.vRect,debugLinePaint);
                                }
                            } else if(tile.loading&&debug){
                                canvas.drawText("LOADING",tile.vRect.left+px(5),tile.vRect.top+px(35),debugTextPaint);
                            }
                            if(tile.visible&&debug){
                                canvas.drawText("ISS "+tile.sampleSize+" RECT "+tile.sRect.top+","+tile.sRect.left+","+
                                                tile.sRect.bottom+","+tile.sRect.right,
                                                tile.vRect.left+px(5),
                                                tile.vRect.top+px(15),
                                                debugTextPaint);
                            }
                        }
                    }
                }

            } else if(bitmap!=null&&!bitmap.isRecycled()){

                float xScale=scale, yScale=scale;
                if(bitmapIsPreview){
                    xScale=scale*((float)sWidth/bitmap.getWidth());
                    yScale=scale*((float)sHeight/bitmap.getHeight());
                }

                if(matrix==null){ matrix=new Matrix(); }
                matrix.reset();
                matrix.postScale(xScale,yScale);
                matrix.postRotate(getRequiredRotation());
                matrix.postTranslate(vTranslate.x,vTranslate.y);

                if(getRequiredRotation()==ORIENTATION_180){
                    matrix.postTranslate(scale*sWidth,scale*sHeight);
                } else if(getRequiredRotation()==ORIENTATION_90){
                    matrix.postTranslate(scale*sHeight,0);
                } else if(getRequiredRotation()==ORIENTATION_270){
                    matrix.postTranslate(0,scale*sWidth);
                }

                if(tileBgPaint!=null){
                    if(sRect==null){ sRect=new RectF(); }
                    sRect.set(0f,
                              0f,
                              bitmapIsPreview ? bitmap.getWidth() : sWidth,
                              bitmapIsPreview ? bitmap.getHeight() : sHeight);
                    matrix.mapRect(sRect);
                    canvas.drawRect(sRect,tileBgPaint);
                }
                canvas.drawBitmap(bitmap,matrix,bitmapPaint);

            }

            if(debug){
                canvas.drawText("Scale: "+String.format(Locale.ENGLISH,"%.2f",scale)+" ("+
                                String.format(Locale.ENGLISH,"%.2f",minScale())+" - "+
                                String.format(Locale.ENGLISH,"%.2f",maxScale)+")",px(5),px(15),debugTextPaint);
                canvas.drawText("Translate: "+String.format(Locale.ENGLISH,"%.2f",vTranslate.x)+":"+
                                String.format(Locale.ENGLISH,"%.2f",vTranslate.y),px(5),px(30),debugTextPaint);
                PointF center=getCenter();
                //noinspection ConstantConditions
                canvas.drawText("Source center: "+String.format(Locale.ENGLISH,"%.2f",center.x)+":"+
                                String.format(Locale.ENGLISH,"%.2f",center.y),px(5),px(45),debugTextPaint);
                if(anim!=null){
                    PointF vCenterStart=sourceToViewCoord(anim.sCenterStart);
                    PointF vCenterEndRequested=sourceToViewCoord(anim.sCenterEndRequested);
                    PointF vCenterEnd=sourceToViewCoord(anim.sCenterEnd);
                    //noinspection ConstantConditions
                    canvas.drawCircle(vCenterStart.x, vCenterStart.y, px(10), debugLinePaint);
                    debugLinePaint.setColor(Color.RED);
                    //noinspection ConstantConditions
                    canvas.drawCircle(vCenterEndRequested.x, vCenterEndRequested.y, px(20), debugLinePaint);
                    debugLinePaint.setColor(Color.BLUE);
                    //noinspection ConstantConditions
                    canvas.drawCircle(vCenterEnd.x, vCenterEnd.y, px(25), debugLinePaint);
                    debugLinePaint.setColor(Color.CYAN);
                    canvas.drawCircle(getWidth() / 2, getHeight() / 2, px(30), debugLinePaint);
                }
                if (vCenterStart != null) {
                    debugLinePaint.setColor(Color.RED);
                    canvas.drawCircle(vCenterStart.x, vCenterStart.y, px(20), debugLinePaint);
                }
                if (quickScaleSCenter != null) {
                    debugLinePaint.setColor(Color.BLUE);
                    canvas.drawCircle(sourceToViewX(quickScaleSCenter.x),
                            sourceToViewY(quickScaleSCenter.y),
                            px(35),
                            debugLinePaint);
                }
                if (quickScaleVStart != null && isQuickScaling) {
                    debugLinePaint.setColor(Color.CYAN);
                    canvas.drawCircle(quickScaleVStart.x, quickScaleVStart.y, px(30), debugLinePaint);
                }
                debugLinePaint.setColor(Color.MAGENTA);
            }
        }
    }

    protected void setMatrixArray(
            float[] array, float f0, float f1, float f2, float f3, float f4, float f5, float f6, float f7) {
        array[0] = f0;
        array[1] = f1;
        array[2] = f2;
        array[3] = f3;
        array[4] = f4;
        array[5] = f5;
        array[6] = f6;
        array[7] = f7;
    }

    protected boolean isBaseLayerReady() {
        if (bitmap != null && !bitmapIsPreview) {
            return true;
        } else if (tileMap != null) {
            boolean baseLayerReady = true;
            for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
                if (tileMapEntry.getKey() == fullImageSampleSize) {
                    for (Tile tile : tileMapEntry.getValue()) {
                        if (tile.loading || tile.bitmap == null) {
                            baseLayerReady = false;
                        }
                    }
                }
            }
            return baseLayerReady;
        }
        return false;
    }

    protected boolean checkReady() {
        boolean ready = getWidth() > 0 && getHeight() > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || isBaseLayerReady());
        if (!readySent && ready) {
            preDraw();
            readySent = true;
            onReady();
            if (onImageEventListener != null) {
                onImageEventListener.onReady();
            }
        }
        return ready;
    }

    protected boolean checkImageLoaded() {
        boolean imageLoaded = isBaseLayerReady();
        if (!imageLoadedSent && imageLoaded) {
            preDraw();
            imageLoadedSent = true;
            onImageLoaded();
            if (onImageEventListener != null) {
                onImageEventListener.onImageLoaded();
            }
        }
        return imageLoaded;
    }

    protected void createPaints() {
        if (bitmapPaint == null) {
            bitmapPaint = new Paint();
            bitmapPaint.setAntiAlias(true);
            bitmapPaint.setFilterBitmap(true);
            bitmapPaint.setDither(true);
        }
        if ((debugTextPaint == null || debugLinePaint == null) && debug) {
            debugTextPaint = new Paint();
            debugTextPaint.setTextSize(px(12));
            debugTextPaint.setColor(Color.MAGENTA);
            debugTextPaint.setStyle(Style.FILL);
            debugLinePaint = new Paint();
            debugLinePaint.setColor(Color.MAGENTA);
            debugLinePaint.setStyle(Style.STROKE);
            debugLinePaint.setStrokeWidth(px(1));
        }
    }

    protected synchronized void initialiseBaseLayer(@NonNull Point maxTileDimensions) {
        debug("initialiseBaseLayer maxTileDimensions=%dx%d", maxTileDimensions.x, maxTileDimensions.y);

        satTemp = new ScaleAndTranslate(0f, new PointF(0, 0));
        fitToBounds(true, satTemp);

        // Load double resolution - next level will be split into four tiles and at the center all four are required,
        // so don't bother with tiling until the next level 16 tiles are needed.
        fullImageSampleSize = calculateInSampleSize(satTemp.scale);
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2;
        }

        if (fullImageSampleSize == 1 && sRegion == null && sWidth() < maxTileDimensions.x && sHeight() < maxTileDimensions.y) {

            // Whole image is required at native resolution, and is smaller than the canvas max bitmap size.
            // Use BitmapDecoder for better image support.
            decoder.recycle();
            decoder = null;
            BitmapLoadTask task = new BitmapLoadTask(this, getContext(), bitmapDecoderFactory, uri, false);
            execute(task);

        } else {

            initialiseTileMap(maxTileDimensions);

            List<Tile> baseGrid = tileMap.get(fullImageSampleSize);
            for (Tile baseTile : baseGrid) {
                TileLoadTask task = new TileLoadTask(this, decoder, baseTile);
                execute(task);
            }
            refreshRequiredTiles(true);

        }

    }

    protected void refreshRequiredTiles(boolean load) {
        if (decoder == null || tileMap == null) {
            return;
        }

        int sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize(scale));

        // Load tiles of the correct sample size that are on screen. Discard tiles off screen, and those that are higher
        // resolution than required, or lower res than required but not the base layer, so the base layer is always present.
        for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
            for (Tile tile : tileMapEntry.getValue()) {
                if (tile.sampleSize < sampleSize || (tile.sampleSize > sampleSize && tile.sampleSize != fullImageSampleSize)) {
                    tile.visible = false;
                    if (tile.bitmap != null) {
                        tile.bitmap.recycle();
                        tile.bitmap = null;
                    }
                }
                if (tile.sampleSize == sampleSize) {
                    if (tileVisible(tile)) {
                        tile.visible = true;
                        if (!tile.loading && tile.bitmap == null && load) {
                            TileLoadTask task = new TileLoadTask(this, decoder, tile);
                            execute(task);
                        }
                    } else if (tile.sampleSize != fullImageSampleSize) {
                        tile.visible = false;
                        if (tile.bitmap != null) {
                            tile.bitmap.recycle();
                            tile.bitmap = null;
                        }
                    }
                } else if (tile.sampleSize == fullImageSampleSize) {
                    tile.visible = true;
                }
            }
        }

    }

    protected boolean tileVisible(Tile tile) {
        float sVisLeft = viewToSourceX(0), sVisRight = viewToSourceX(getWidth()), sVisTop = viewToSourceY(0), sVisBottom =
                viewToSourceY(getHeight());
        return !(sVisLeft > tile.sRect.right || tile.sRect.left > sVisRight || sVisTop > tile.sRect.bottom ||
                tile.sRect.top > sVisBottom);
    }

    protected void preDraw() {
        if (getWidth() == 0 || getHeight() == 0 || sWidth <= 0 || sHeight <= 0) {
            return;
        }

        // If waiting to translate to new center position, set translate now
        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale;
            if (vTranslate == null) {
                vTranslate = new PointF();
            }
            vTranslate.x = (getWidth() / 2) - (scale * sPendingCenter.x);
            vTranslate.y = (getHeight() / 2) - (scale * sPendingCenter.y);
            sPendingCenter = null;
            pendingScale = null;
            fitToBounds(true);
            refreshRequiredTiles(true);
        }

        // On first display of base image set up position, and in other cases make sure scale is correct.
        fitToBounds(false);
    }

    protected int calculateInSampleSize(float scale) {
        if (minimumTileDpi > 0) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
            scale = (minimumTileDpi / averageDpi) * scale;
        }

        int reqWidth = (int) (sWidth() * scale);
        int reqHeight = (int) (sHeight() * scale);

        // Raw height and width of image
        int inSampleSize = 1;
        if (reqWidth == 0 || reqHeight == 0) {
            return 32;
        }

        if (sHeight() > reqHeight || sWidth() > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) sHeight() / (float) reqHeight);
            final int widthRatio = Math.round((float) sWidth() / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        // We want the actual sample size that will be used, so round down to nearest power of 2.
        int power = 1;
        while (power * 2 < inSampleSize) {
            power = power * 2;
        }

        return power;
    }

    protected void fitToBounds(boolean center, ScaleAndTranslate sat) {
        if (panLimit == PAN_LIMIT_OUTSIDE && isReady()) {
            center = false;
        }

        PointF vTranslate = sat.vTranslate;
        float scale = limitedScale(sat.scale);
        float scaleWidth = scale * sWidth();
        float scaleHeight = scale * sHeight();

        if (panLimit == PAN_LIMIT_CENTER && isReady()) {
            vTranslate.x = Math.max(vTranslate.x, getWidth() / 2 - scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, getHeight() / 2 - scaleHeight);
        } else if (center) {
            vTranslate.x = Math.max(vTranslate.x, getWidth() - scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, getHeight() - scaleHeight);
        } else {
            vTranslate.x = Math.max(vTranslate.x, -scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, -scaleHeight);
        }

        // Asymmetric padding adjustments
        float xPaddingRatio =
                getPaddingLeft() > 0 || getPaddingRight() > 0 ? getPaddingLeft() / (float) (getPaddingLeft() + getPaddingRight()) :
                        0.5f;
        float yPaddingRatio =
                getPaddingTop() > 0 || getPaddingBottom() > 0 ? getPaddingTop() / (float) (getPaddingTop() + getPaddingBottom()) :
                        0.5f;

        float maxTx;
        float maxTy;
        if (panLimit == PAN_LIMIT_CENTER && isReady()) {
            maxTx = Math.max(0, getWidth() / 2);
            maxTy = Math.max(0, getHeight() / 2);
        } else if (center) {
            maxTx = Math.max(0, (getWidth() - scaleWidth) * xPaddingRatio);
            maxTy = Math.max(0, (getHeight() - scaleHeight) * yPaddingRatio);
        } else {
            maxTx = Math.max(0, getWidth());
            maxTy = Math.max(0, getHeight());
        }

        vTranslate.x = Math.min(vTranslate.x, maxTx);
        vTranslate.y = Math.min(vTranslate.y, maxTy);

        sat.scale = scale;
    }

    protected void fitToBounds(boolean center) {
        boolean init = false;
        if (vTranslate == null) {
            init = true;
            vTranslate = new PointF(0, 0);
        }
        if (satTemp == null) {
            satTemp = new ScaleAndTranslate(0, new PointF(0, 0));
        }
        satTemp.scale = scale;
        satTemp.vTranslate.set(vTranslate);
        fitToBounds(center, satTemp);
        scale = satTemp.scale;
        vTranslate.set(satTemp.vTranslate);
        if (init && minimumScaleType != SCALE_TYPE_START) {
            vTranslate.set(vTranslateForSCenter(sWidth() / 2, sHeight() / 2, scale));
        }
    }

    protected void initialiseTileMap(Point maxTileDimensions) {
        debug("initialiseTileMap maxTileDimensions=%dx%d", maxTileDimensions.x, maxTileDimensions.y);
        this.tileMap = new LinkedHashMap<>();
        int sampleSize = fullImageSampleSize;
        int xTiles = 1;
        int yTiles = 1;
        while (true) {
            int sTileWidth = sWidth() / xTiles;
            int sTileHeight = sHeight() / yTiles;
            int subTileWidth = sTileWidth / sampleSize;
            int subTileHeight = sTileHeight / sampleSize;
            while (subTileWidth + xTiles + 1 > maxTileDimensions.x ||
                    (subTileWidth > getWidth() * 1.25 && sampleSize < fullImageSampleSize)) {
                xTiles += 1;
                sTileWidth = sWidth() / xTiles;
                subTileWidth = sTileWidth / sampleSize;
            }
            while (subTileHeight + yTiles + 1 > maxTileDimensions.y ||
                    (subTileHeight > getHeight() * 1.25 && sampleSize < fullImageSampleSize)) {
                yTiles += 1;
                sTileHeight = sHeight() / yTiles;
                subTileHeight = sTileHeight / sampleSize;
            }
            List<Tile> tileGrid = new ArrayList<>(xTiles * yTiles);
            for (int x = 0; x < xTiles; x++) {
                for (int y = 0; y < yTiles; y++) {
                    Tile tile = new Tile();
                    tile.sampleSize = sampleSize;
                    tile.visible = sampleSize == fullImageSampleSize;
                    tile.sRect = new Rect(x * sTileWidth,
                            y * sTileHeight,
                            x == xTiles - 1 ? sWidth() : (x + 1) * sTileWidth,
                            y == yTiles - 1 ? sHeight() : (y + 1) * sTileHeight);
                    tile.vRect = new Rect(0, 0, 0, 0);
                    tile.fileSRect = new Rect(tile.sRect);
                    tileGrid.add(tile);
                }
            }
            tileMap.put(sampleSize, tileGrid);
            if (sampleSize == 1) {
                break;
            } else {
                sampleSize /= 2;
            }
        }
    }

    protected synchronized void onTilesInited(ImageRegionDecoder decoder, int sWidth, int sHeight, int sOrientation) {
        debug("onTilesInited sWidth=%d, sHeight=%d, sOrientation=%d", sWidth, sHeight, sOrientation);
        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != sWidth || this.sHeight != sHeight)) {
            reset(false);
            if (bitmap != null) {
                if (!bitmapIsCached) {
                    bitmap.recycle();
                }
                bitmap = null;
                if (onImageEventListener != null && bitmapIsCached) {
                    onImageEventListener.onPreviewReleased();
                }
                bitmapIsPreview = false;
                bitmapIsCached = false;
            }
        }
        this.isCallSuperOnDraw = false;
        this.decoder = decoder;
        this.sWidth = sWidth;
        this.sHeight = sHeight;
        this.sOrientation = sOrientation;
        checkReady();
        if (!checkImageLoaded() && maxTileWidth > 0 && maxTileWidth != TILE_SIZE_AUTO && maxTileHeight > 0 &&
                maxTileHeight != TILE_SIZE_AUTO && getWidth() > 0 && getHeight() > 0) {
            initialiseBaseLayer(new Point(maxTileWidth, maxTileHeight));
        }
        invalidate();
        requestLayout();
    }

    protected synchronized void onTileLoaded() {
        debug("onTileLoaded");
        checkReady();
        checkImageLoaded();
        if (isBaseLayerReady() && bitmap != null) {
            if (!bitmapIsCached) {
                bitmap.recycle();
            }
            bitmap = null;
            if (onImageEventListener != null && bitmapIsCached) {
                onImageEventListener.onPreviewReleased();
            }
            bitmapIsPreview = false;
            bitmapIsCached = false;
        }
        invalidate();
    }

    protected synchronized void onPreviewLoaded(Bitmap previewBitmap) {
        debug("onPreviewLoaded");
        if (bitmap != null || imageLoadedSent) {
            previewBitmap.recycle();
            return;
        }
        if (pRegion != null) {
            bitmap = Bitmap.createBitmap(previewBitmap, pRegion.left, pRegion.top, pRegion.width(), pRegion.height());
        } else {
            bitmap = previewBitmap;
        }
        this.isCallSuperOnDraw = false;
        bitmapIsPreview = true;
        if (checkReady()) {
            invalidate();
            requestLayout();
        }
    }

    protected synchronized void onImageLoaded(Bitmap bitmap, int sOrientation, boolean bitmapIsCached) {
        debug("onImageLoaded");
        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != bitmap.getWidth() || this.sHeight != bitmap.getHeight())) {
            reset(false);
        }
        if (this.bitmap != null && !this.bitmapIsCached) {
            this.bitmap.recycle();
        }

        if (this.bitmap != null && this.bitmapIsCached && onImageEventListener != null) {
            onImageEventListener.onPreviewReleased();
        }
        this.isCallSuperOnDraw = false;
        this.bitmapIsPreview = false;
        this.bitmapIsCached = bitmapIsCached;
        this.bitmap = bitmap;
        this.sWidth = bitmap.getWidth();
        this.sHeight = bitmap.getHeight();
        this.sOrientation = sOrientation;
        boolean ready = checkReady();
        boolean imageLoaded = checkImageLoaded();
        if (ready || imageLoaded) {
            invalidate();
            requestLayout();
        }
    }

    protected void execute(AsyncTask<Void, Void, ?> asyncTask) {
        asyncTask.executeOnExecutor(executor);
    }

    @Override
    public Drawable getDrawable() {
        if (!isCallSuperOnDraw) {
            return null;
        }
        return super.getDrawable();
    }

    @AnyThread
    protected int getExifOrientation(Context context, String sourceUri) {
        int exifOrientation = ORIENTATION_0;
        if (sourceUri.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Cursor cursor = null;
            try {
                String[] columns = {MediaStore.Images.Media.ORIENTATION};
                cursor = context.getContentResolver().query(Uri.parse(sourceUri), columns, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int orientation = cursor.getInt(0);
                        if (VALID_ORIENTATIONS.contains(orientation) && orientation != ORIENTATION_USE_EXIF) {
                            exifOrientation = orientation;
                        } else {
                            Log.w(TAG, "Unsupported orientation: " + orientation);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get orientation of image from media store");
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else if (sourceUri.startsWith(ImageSource.FILE_SCHEME) && !sourceUri.startsWith(ImageSource.ASSET_SCHEME)) {
            try {
                ExifInterface exifInterface = new ExifInterface(sourceUri.substring(ImageSource.FILE_SCHEME.length() - 1));
                int orientationAttr = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                if (orientationAttr == ExifInterface.ORIENTATION_NORMAL ||
                        orientationAttr == ExifInterface.ORIENTATION_UNDEFINED) {
                    exifOrientation = ORIENTATION_0;
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_90) {
                    exifOrientation = ORIENTATION_90;
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_180) {
                    exifOrientation = ORIENTATION_180;
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_270) {
                    exifOrientation = ORIENTATION_270;
                } else {
                    Log.w(TAG, "Unsupported EXIF orientation: " + orientationAttr);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get EXIF orientation of image");
            }
        } else {
            exifOrientation = ImageDecoder2.getOrientation(context, Uri.parse(sourceUri));
        }
        return exifOrientation;
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        isCallSuperOnDraw = true;
        if (bitmap != null) {
            bitmap.recycle();
        }
        bitmap = null;
        super.setImageBitmap(bm);
    }

    protected void restoreState(ImageViewState state) {
        if (state != null && VALID_ORIENTATIONS.contains(state.getOrientation())) {
            this.orientation = state.getOrientation();
            this.pendingScale = state.getScale();
            this.sPendingCenter = state.getCenter();
            invalidate();
        }
    }

    public void setMaxTileSize(int maxPixels) {
        this.maxTileWidth = maxPixels;
        this.maxTileHeight = maxPixels;
    }

    public void setMaxTileSize(int maxPixelsX, int maxPixelsY) {
        this.maxTileWidth = maxPixelsX;
        this.maxTileHeight = maxPixelsY;
    }

    @NonNull
    protected Point getMaxBitmapDimensions(Canvas canvas) {
        return new Point(Math.min(canvas.getMaximumBitmapWidth(), maxTileWidth),
                Math.min(canvas.getMaximumBitmapHeight(), maxTileHeight));
    }

    @Override
    public void setImageURI(Uri uri) {
        isCallSuperOnDraw = true;
        if (bitmap != null) {
            bitmap.recycle();
        }
        bitmap = null;
        super.setImageURI(uri);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    protected int sWidth() {
        int rotation = getRequiredRotation();
        if (rotation == 90 || rotation == 270) {
            return sHeight;
        } else {
            return sWidth;
        }
    }

    @Override
    public void setImageResource(int resId) {
        isCallSuperOnDraw = true;
        if (bitmap != null) {
            bitmap.recycle();
        }
        bitmap = null;
        super.setImageResource(resId);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    protected int sHeight() {
        int rotation = getRequiredRotation();
        if (rotation == 90 || rotation == 270) {
            return sWidth;
        } else {
            return sHeight;
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @AnyThread
    protected void fileSRect(Rect sRect,Rect target){
        if(getRequiredRotation()==0){
            target.set(sRect);
        } else if(getRequiredRotation()==90){
            target.set(sRect.top,sHeight-sRect.right,sRect.bottom,sHeight-sRect.left);
        } else if(getRequiredRotation()==180){
            target.set(sWidth-sRect.right,sHeight-sRect.bottom,sWidth-sRect.left,sHeight-sRect.top);
        } else{
            target.set(sWidth-sRect.bottom,sRect.left,sWidth-sRect.top,sRect.right);
        }
    }

    @AnyThread
    protected int getRequiredRotation(){
        if(orientation==ORIENTATION_USE_EXIF){
            return sOrientation;
        } else{
            return orientation;
        }
    }

    protected float distance(float x0,float x1,float y0,float y1){
        float x=x0-x1;
        float y=y0-y1;
        return (float)Math.sqrt(x*x+y*y);
    }

    public void recycle(){
        reset(true);
        bitmapPaint=null;
        debugTextPaint=null;
        debugLinePaint=null;
        tileBgPaint=null;
    }

    protected float viewToSourceX(float vx){
        if(vTranslate==null){ return Float.NaN; }
        return (vx-vTranslate.x)/scale;
    }

    protected float viewToSourceY(float vy){
        if(vTranslate==null){ return Float.NaN; }
        return (vy-vTranslate.y)/scale;
    }

    public void viewToFileRect(Rect vRect,Rect fRect){
        if(vTranslate==null||!readySent){
            return;
        }
        fRect.set((int)viewToSourceX(vRect.left),
                  (int)viewToSourceY(vRect.top),
                  (int)viewToSourceX(vRect.right),
                  (int)viewToSourceY(vRect.bottom));
        fileSRect(fRect,fRect);
        fRect.set(Math.max(0,fRect.left),
                  Math.max(0,fRect.top),
                  Math.min(sWidth,fRect.right),
                  Math.min(sHeight,fRect.bottom));
        if(sRegion!=null){
            fRect.offset(sRegion.left,sRegion.top);
        }
    }

    public void visibleFileRect(Rect fRect){
        if(vTranslate==null||!readySent){
            return;
        }
        fRect.set(0,0,getWidth(),getHeight());
        viewToFileRect(fRect,fRect);
    }

    @Nullable
    public final PointF viewToSourceCoord(PointF vxy){
        return viewToSourceCoord(vxy.x,vxy.y,new PointF());
    }

    @Nullable
    public final PointF viewToSourceCoord(float vx,float vy){
        return viewToSourceCoord(vx,vy,new PointF());
    }

    @Nullable
    public final PointF viewToSourceCoord(PointF vxy,@NonNull PointF sTarget){
        return viewToSourceCoord(vxy.x,vxy.y,sTarget);
    }

    @Nullable
    public final PointF viewToSourceCoord(float vx,float vy,@NonNull PointF sTarget){
        if(vTranslate==null){
            return null;
        }
        sTarget.set(viewToSourceX(vx),viewToSourceY(vy));
        return sTarget;
    }

    protected float sourceToViewX(float sx){
        if(vTranslate==null){ return Float.NaN; }
        return (sx*scale)+vTranslate.x;
    }

    protected float sourceToViewY(float sy){
        if(vTranslate==null){ return Float.NaN; }
        return (sy*scale)+vTranslate.y;
    }

    @Nullable
    public final PointF sourceToViewCoord(PointF sxy){
        return sourceToViewCoord(sxy.x,sxy.y,new PointF());
    }

    @Nullable
    public final PointF sourceToViewCoord(float sx,float sy){
        return sourceToViewCoord(sx,sy,new PointF());
    }

    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    public final PointF sourceToViewCoord(PointF sxy,@NonNull PointF vTarget){
        return sourceToViewCoord(sxy.x,sxy.y,vTarget);
    }

    @Nullable
    public final PointF sourceToViewCoord(float sx,float sy,@NonNull PointF vTarget){
        if(vTranslate==null){
            return null;
        }
        vTarget.set(sourceToViewX(sx),sourceToViewY(sy));
        return vTarget;
    }

    protected void sourceToViewRect(@NonNull Rect sRect,@NonNull Rect vTarget){
        vTarget.set((int)sourceToViewX(sRect.left),
                    (int)sourceToViewY(sRect.top),
                    (int)sourceToViewX(sRect.right),
                    (int)sourceToViewY(sRect.bottom));
    }

    @NonNull
    protected PointF vTranslateForSCenter(float sCenterX,float sCenterY,float scale){
        int vxCenter=getPaddingLeft()+(getWidth()-getPaddingRight()-getPaddingLeft())/2;
        int vyCenter=getPaddingTop()+(getHeight()-getPaddingBottom()-getPaddingTop())/2;
        if(satTemp==null){
            satTemp=new ScaleAndTranslate(0,new PointF(0,0));
        }
        satTemp.scale=scale;
        satTemp.vTranslate.set(vxCenter-(sCenterX*scale),vyCenter-(sCenterY*scale));
        fitToBounds(true,satTemp);
        return satTemp.vTranslate;
    }

    @NonNull
    protected PointF limitedSCenter(float sCenterX,float sCenterY,float scale,@NonNull PointF sTarget){
        PointF vTranslate=vTranslateForSCenter(sCenterX,sCenterY,scale);
        int vxCenter=getPaddingLeft()+(getWidth()-getPaddingRight()-getPaddingLeft())/2;
        int vyCenter=getPaddingTop()+(getHeight()-getPaddingBottom()-getPaddingTop())/2;
        float sx=(vxCenter-vTranslate.x)/scale;
        float sy=(vyCenter-vTranslate.y)/scale;
        sTarget.set(sx,sy);
        return sTarget;
    }

    protected float minScale(){
        int vPadding=getPaddingBottom()+getPaddingTop();
        int hPadding=getPaddingLeft()+getPaddingRight();
        if(minimumScaleType==SCALE_TYPE_CENTER_CROP||minimumScaleType==SCALE_TYPE_START){
            return Math.max((getWidth()-hPadding)/(float)sWidth(),(getHeight()-vPadding)/(float)sHeight());
        } else if(minimumScaleType==SCALE_TYPE_CUSTOM&&minScale>0){
            return minScale;
        } else{
            return Math.min((getWidth()-hPadding)/(float)sWidth(),(getHeight()-vPadding)/(float)sHeight());
        }
    }

    protected float limitedScale(float targetScale){
        targetScale=Math.max(minScale(),targetScale);
        targetScale=Math.min(maxScale,targetScale);
        return targetScale;
    }

    protected float ease(int type,long time,float from,float change,long duration){
        switch(type){
            case EASE_IN_OUT_QUAD:
                return easeInOutQuad(time,from,change,duration);
            case EASE_OUT_QUAD:
                return easeOutQuad(time,from,change,duration);
            default:
                throw new IllegalStateException("Unexpected easing type: "+type);
        }
    }

    protected float easeOutQuad(long time,float from,float change,long duration){
        float progress=(float)time/(float)duration;
        return -change*progress*(progress-2)+from;
    }

    protected float easeInOutQuad(long time,float from,float change,long duration){
        float timeF=time/(duration/2f);
        if(timeF<1){
            return (change/2f*timeF*timeF)+from;
        } else{
            timeF--;
            return (-change/2f)*(timeF*(timeF-2)-1)+from;
        }
    }

    @AnyThread
    protected void debug(String message,Object... args){
        if(debug){
            Log.d(TAG,String.format(message,args));
        }
    }

    protected int px(int px){
        return (int)(density*px);
    }

    public final void setRegionDecoderClass(@NonNull Class<? extends ImageRegionDecoder> regionDecoderClass){
        //noinspection ConstantConditions
        if(regionDecoderClass==null){
            throw new IllegalArgumentException("Decoder class cannot be set to null");
        }
        this.regionDecoderFactory=new CompatDecoderFactory<>(regionDecoderClass);
    }

    public final void setRegionDecoderFactory(@NonNull DecoderFactory<? extends ImageRegionDecoder> regionDecoderFactory){
        //noinspection ConstantConditions
        if(regionDecoderFactory==null){
            throw new IllegalArgumentException("Decoder factory cannot be set to null");
        }
        this.regionDecoderFactory=regionDecoderFactory;
    }

    public final void setBitmapDecoderClass(@NonNull Class<? extends ImageDecoder> bitmapDecoderClass){
        //noinspection ConstantConditions
        if(bitmapDecoderClass==null){
            throw new IllegalArgumentException("Decoder class cannot be set to null");
        }
        this.bitmapDecoderFactory=new CompatDecoderFactory<>(bitmapDecoderClass);
    }

    public final void setBitmapDecoderFactory(@NonNull DecoderFactory<? extends ImageDecoder> bitmapDecoderFactory){
        //noinspection ConstantConditions
        if(bitmapDecoderFactory==null){
            throw new IllegalArgumentException("Decoder factory cannot be set to null");
        }
        this.bitmapDecoderFactory=bitmapDecoderFactory;
    }

    public final void getPanRemaining(RectF vTarget){
        if(!isReady()){
            return;
        }

        float scaleWidth=scale*sWidth();
        float scaleHeight=scale*sHeight();

        if(panLimit==PAN_LIMIT_CENTER){
            vTarget.top=Math.max(0,-(vTranslate.y-(getHeight()/2)));
            vTarget.left=Math.max(0,-(vTranslate.x-(getWidth()/2)));
            vTarget.bottom=Math.max(0,vTranslate.y-((getHeight()/2)-scaleHeight));
            vTarget.right=Math.max(0,vTranslate.x-((getWidth()/2)-scaleWidth));
        } else if(panLimit==PAN_LIMIT_OUTSIDE){
            vTarget.top=Math.max(0,-(vTranslate.y-getHeight()));
            vTarget.left=Math.max(0,-(vTranslate.x-getWidth()));
            vTarget.bottom=Math.max(0,vTranslate.y+scaleHeight);
            vTarget.right=Math.max(0,vTranslate.x+scaleWidth);
        } else{
            vTarget.top=Math.max(0,-vTranslate.y);
            vTarget.left=Math.max(0,-vTranslate.x);
            vTarget.bottom=Math.max(0,(scaleHeight+vTranslate.y)-getHeight());
            vTarget.right=Math.max(0,(scaleWidth+vTranslate.x)-getWidth());
        }
    }

    public final void setPanLimit(int panLimit){
        if(!VALID_PAN_LIMITS.contains(panLimit)){
            throw new IllegalArgumentException("Invalid pan limit: "+panLimit);
        }
        this.panLimit=panLimit;
        if(isReady()){
            fitToBounds(true);
            invalidate();
        }
    }

    public final void setMinimumScaleType(int scaleType){
        if(!VALID_SCALE_TYPES.contains(scaleType)){
            throw new IllegalArgumentException("Invalid scale type: "+scaleType);
        }
        this.minimumScaleType=scaleType;
        if(isReady()){
            fitToBounds(true);
            invalidate();
        }
    }

    public final void setMaxScale(float maxScale){
        this.maxScale=maxScale;
    }

    public final void setMinScale(float minScale){
        this.minScale=minScale;
    }

    public final void setMinimumDpi(int dpi){
        DisplayMetrics metrics=getResources().getDisplayMetrics();
        float averageDpi=(metrics.xdpi+metrics.ydpi)/2;
        setMaxScale(averageDpi/dpi);
    }

    public final void setMaximumDpi(int dpi){
        DisplayMetrics metrics=getResources().getDisplayMetrics();
        float averageDpi=(metrics.xdpi+metrics.ydpi)/2;
        setMinScale(averageDpi/dpi);
    }

    public float getMaxScale(){
        return maxScale;
    }

    public final float getMinScale(){
        return minScale();
    }

    public void setMinimumTileDpi(int minimumTileDpi){
        DisplayMetrics metrics=getResources().getDisplayMetrics();
        float averageDpi=(metrics.xdpi+metrics.ydpi)/2;
        this.minimumTileDpi=(int)Math.min(averageDpi,minimumTileDpi);
        if(isReady()){
            reset(false);
            invalidate();
        }
    }

    @Nullable
    public final PointF getCenter(){
        int mX=getWidth()/2;
        int mY=getHeight()/2;
        return viewToSourceCoord(mX,mY);
    }

    public final float getScale(){
        if(isCallSuperOnDraw){
            return super.getScale();
        }
        return scale;
    }

    public final void setScaleAndCenter(float scale,@Nullable PointF sCenter){
        this.anim=null;
        this.pendingScale=scale;
        this.sPendingCenter=sCenter;
        this.sRequestedCenter=sCenter;
        invalidate();
    }

    public final void resetScaleAndCenter(){
        this.anim=null;
        this.pendingScale=limitedScale(0);
        if(isReady()){
            this.sPendingCenter=new PointF(sWidth()/2,sHeight()/2);
        } else{
            this.sPendingCenter=new PointF(0,0);
        }
        invalidate();
    }

    public final boolean isReady(){
        return readySent;
    }

    @SuppressWarnings("EmptyMethod")
    protected void onReady(){

    }

    public final boolean isImageLoaded(){
        return imageLoadedSent;
    }

    @SuppressWarnings("EmptyMethod")
    protected void onImageLoaded(){

    }

    public final int getSWidth(){
        return sWidth;
    }

    public final int getSHeight(){
        return sHeight;
    }

    public final int getOrientation(){
        return orientation;
    }

    public final int getAppliedOrientation(){
        return getRequiredRotation();
    }

    @Nullable
    public final ImageViewState getState(){
        if(vTranslate!=null&&sWidth>0&&sHeight>0){
            //noinspection ConstantConditions
            return new ImageViewState(getScale(),getCenter(),getOrientation());
        }
        return null;
    }

    public final boolean isZoomEnabled(){
        return zoomEnabled;
    }

    public final void setZoomEnabled(boolean zoomEnabled){
        this.zoomEnabled=zoomEnabled;
    }

    public final boolean isQuickScaleEnabled(){
        return quickScaleEnabled;
    }

    public final void setQuickScaleEnabled(boolean quickScaleEnabled){
        this.quickScaleEnabled=quickScaleEnabled;
    }

    public final boolean isPanEnabled(){
        return panEnabled;
    }

    public final void setPanEnabled(boolean panEnabled){
        this.panEnabled=panEnabled;
        if(!panEnabled&&vTranslate!=null){
            vTranslate.x=(getWidth()/2)-(scale*(sWidth()/2));
            vTranslate.y=(getHeight()/2)-(scale*(sHeight()/2));
            if(isReady()){
                refreshRequiredTiles(true);
                invalidate();
            }
        }
    }

    public final void setTileBackgroundColor(int tileBgColor){
        if(Color.alpha(tileBgColor)==0){
            tileBgPaint=null;
        } else{
            tileBgPaint=new Paint();
            tileBgPaint.setStyle(Style.FILL);
            tileBgPaint.setColor(tileBgColor);
        }
        invalidate();
    }

    public final void setDoubleTapZoomScale(float doubleTapZoomScale){
        this.doubleTapZoomScale=doubleTapZoomScale;
    }

    public final void setDoubleTapZoomDpi(int dpi){
        DisplayMetrics metrics=getResources().getDisplayMetrics();
        float averageDpi=(metrics.xdpi+metrics.ydpi)/2;
        setDoubleTapZoomScale(averageDpi/dpi);
    }

    public final void setDoubleTapZoomStyle(int doubleTapZoomStyle){
        if(!VALID_ZOOM_STYLES.contains(doubleTapZoomStyle)){
            throw new IllegalArgumentException("Invalid zoom style: "+doubleTapZoomStyle);
        }
        this.doubleTapZoomStyle=doubleTapZoomStyle;
    }

    public final void setDoubleTapZoomDuration(int durationMs){
        this.doubleTapZoomDuration=Math.max(0,durationMs);
    }

    public void setExecutor(@NonNull Executor executor){
        //noinspection ConstantConditions
        if(executor==null){
            throw new NullPointerException("Executor must not be null");
        }
        this.executor=executor;
    }

    public void setEagerLoadingEnabled(boolean eagerLoadingEnabled){
        this.eagerLoadingEnabled=eagerLoadingEnabled;
    }

    public final void setDebug(boolean debug){
        this.debug=debug;
    }

    public boolean hasImage(){
        return uri!=null||bitmap!=null;
    }


    public void setOnImageEventListener(OnImageEventListener onImageEventListener){
        this.onImageEventListener=onImageEventListener;
    }

    public void setOnStateChangedListener(OnStateChangedListener onStateChangedListener){
        this.onStateChangedListener=onStateChangedListener;
    }

    protected void sendStateChanged(float oldScale,PointF oldVTranslate,int origin){
        if(onStateChangedListener!=null&&scale!=oldScale){
            onStateChangedListener.onScaleChanged(scale,origin);
        }
        if(onStateChangedListener!=null&&!vTranslate.equals(oldVTranslate)){
            onStateChangedListener.onCenterChanged(getCenter(),origin);
        }
    }

    @Nullable
    public AnimationBuilder animateCenter(PointF sCenter){
        if(!isReady()){
            return null;
        }
        return new AnimationBuilder(sCenter);
    }

    @Nullable
    public AnimationBuilder animateScale(float scale){
        if(!isReady()){
            return null;
        }
        return new AnimationBuilder(scale);
    }

    @Nullable
    public AnimationBuilder animateScaleAndCenter(float scale,PointF sCenter){
        if(!isReady()){
            return null;
        }
        return new AnimationBuilder(scale,sCenter);
    }

    private volatile boolean isRequestScaleCenter=false;

    /**
     * 请求居中并缩放最小
     */
    public void requestLayoutAndroidScaleCenter(){
        isRequestScaleCenter=!isCallSuperOnDraw;
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);
        if(isRequestScaleCenter){
            setScaleAndCenter(getMinScale(),getCenter());
        }
        isRequestScaleCenter=false;
    }

    public final class AnimationBuilder{

        protected final float targetScale;
        protected final PointF targetSCenter;
        protected final PointF vFocus;
        protected long duration=500;
        protected int easing=EASE_IN_OUT_QUAD;
        protected int origin=ORIGIN_ANIM;
        protected boolean interruptible=true;
        protected boolean panLimited=true;
        protected OnAnimationEventListener listener;

        protected AnimationBuilder(PointF sCenter){
            this.targetScale=scale;
            this.targetSCenter=sCenter;
            this.vFocus=null;
        }

        protected AnimationBuilder(float scale){
            this.targetScale=scale;
            this.targetSCenter=getCenter();
            this.vFocus=null;
        }

        protected AnimationBuilder(float scale,PointF sCenter){
            this.targetScale=scale;
            this.targetSCenter=sCenter;
            this.vFocus=null;
        }

        protected AnimationBuilder(float scale,PointF sCenter,PointF vFocus){
            this.targetScale=scale;
            this.targetSCenter=sCenter;
            this.vFocus=vFocus;
        }

        @NonNull
        public AnimationBuilder withDuration(long duration){
            this.duration=duration;
            return this;
        }

        @NonNull
        public AnimationBuilder withInterruptible(boolean interruptible){
            this.interruptible=interruptible;
            return this;
        }

        @NonNull
        public AnimationBuilder withEasing(int easing){
            if(!VALID_EASING_STYLES.contains(easing)){
                throw new IllegalArgumentException("Unknown easing type: "+easing);
            }
            this.easing=easing;
            return this;
        }

        @NonNull
        public AnimationBuilder withOnAnimationEventListener(OnAnimationEventListener listener){
            this.listener=listener;
            return this;
        }

        @NonNull
        protected AnimationBuilder withPanLimited(boolean panLimited){
            this.panLimited=panLimited;
            return this;
        }

        @NonNull
        protected AnimationBuilder withOrigin(int origin){
            this.origin=origin;
            return this;
        }

        public void start(){
            if(anim!=null&&anim.listener!=null){
                try{
                    anim.listener.onInterruptedByNewAnim();
                } catch(Exception e){
                    Log.w(TAG,"Error thrown by animation listener",e);
                }
            }

            int vxCenter=getPaddingLeft()+(getWidth()-getPaddingRight()-getPaddingLeft())/2;
            int vyCenter=getPaddingTop()+(getHeight()-getPaddingBottom()-getPaddingTop())/2;
            float targetScale=limitedScale(this.targetScale);
            PointF targetSCenter=panLimited ? limitedSCenter(this.targetSCenter.x,
                                                             this.targetSCenter.y,
                                                             targetScale,
                                                             new PointF()) : this.targetSCenter;
            anim=new Anim();
            anim.scaleStart=scale;
            anim.scaleEnd=targetScale;
            anim.time=System.currentTimeMillis();
            anim.sCenterEndRequested=targetSCenter;
            anim.sCenterStart=getCenter();
            anim.sCenterEnd=targetSCenter;
            anim.vFocusStart=sourceToViewCoord(targetSCenter);
            anim.vFocusEnd=new PointF(vxCenter,vyCenter);
            anim.duration=duration;
            anim.interruptible=interruptible;
            anim.easing=easing;
            anim.origin=origin;
            anim.time=System.currentTimeMillis();
            anim.listener=listener;

            if(vFocus!=null){
                // Calculate where translation will be at the end of the anim
                float vTranslateXEnd=vFocus.x-(targetScale*anim.sCenterStart.x);
                float vTranslateYEnd=vFocus.y-(targetScale*anim.sCenterStart.y);
                ScaleAndTranslate satEnd=new ScaleAndTranslate(targetScale,new PointF(vTranslateXEnd,vTranslateYEnd));
                // Fit the end translation into bounds
                fitToBounds(true,satEnd);
                // Adjust the position of the focus point at end so image will be in bounds
                anim.vFocusEnd=new PointF(vFocus.x+(satEnd.vTranslate.x-vTranslateXEnd),
                                          vFocus.y+(satEnd.vTranslate.y-vTranslateYEnd));
            }

            invalidate();
        }

    }

    @SuppressWarnings("EmptyMethod")
    public interface OnAnimationEventListener{

        void onComplete();

        void onInterruptedByUser();

        void onInterruptedByNewAnim();

    }

    public static class DefaultOnAnimationEventListener implements OnAnimationEventListener{

        @Override
        public void onComplete(){ }

        @Override
        public void onInterruptedByUser(){ }

        @Override
        public void onInterruptedByNewAnim(){ }

    }

    @SuppressWarnings("EmptyMethod")
    public interface OnImageEventListener{

        void onReady();

        void onImageLoaded();

        void onPreviewLoadError(Exception e);

        void onImageLoadError(Exception e);

        void onTileLoadError(Exception e);

        void onPreviewReleased();
    }

    public static class DefaultOnImageEventListener implements OnImageEventListener{

        @Override
        public void onReady(){ }

        @Override
        public void onImageLoaded(){ }

        @Override
        public void onPreviewLoadError(Exception e){ }

        @Override
        public void onImageLoadError(Exception e){ }

        @Override
        public void onTileLoadError(Exception e){ }

        @Override
        public void onPreviewReleased(){ }

    }

    @SuppressWarnings("EmptyMethod")
    public interface OnStateChangedListener{

        void onScaleChanged(float newScale,int origin);

        void onCenterChanged(PointF newCenter,int origin);

    }

    public static class DefaultOnStateChangedListener implements OnStateChangedListener{

        @Override
        public void onCenterChanged(PointF newCenter,int origin){ }

        @Override
        public void onScaleChanged(float newScale,int origin){ }

    }

    @Override
    public void setImageDrawable(Drawable drawable){
        isCallSuperOnDraw=true;
        if(bitmap!=null){
            bitmap.recycle();
        }
        bitmap = null;
        super.setImageDrawable(drawable);
    }

    @Override
    public void setOnDoubleTapListener(GestureDetector.OnDoubleTapListener onDoubleTapListener) {
        super.setOnDoubleTapListener(onDoubleTapListener);
        this.onDoubleTapListener = onDoubleTapListener;
    }

    protected static class TilesInitTask extends AsyncTask<Void, Void, int[]> {
        protected final WeakReference<ScaleImageView> viewRef;
        protected final WeakReference<Context> contextRef;
        protected final WeakReference<DecoderFactory<? extends ImageRegionDecoder>> decoderFactoryRef;
        protected final Uri source;
        protected ImageRegionDecoder decoder;
        protected Exception exception;

        TilesInitTask(
                ScaleImageView view,
                Context context,
                DecoderFactory<? extends ImageRegionDecoder> decoderFactory,
                Uri source) {
            this.viewRef = new WeakReference<>(view);
            this.contextRef = new WeakReference<>(context);
            this.decoderFactoryRef = new WeakReference<DecoderFactory<? extends ImageRegionDecoder>>(decoderFactory);
            this.source = source;
        }

        @Override
        protected int[] doInBackground(Void... params) {
            try {
                String sourceUri = source.toString();
                Context context = contextRef.get();
                DecoderFactory<? extends ImageRegionDecoder> decoderFactory = decoderFactoryRef.get();
                ScaleImageView view = viewRef.get();
                if (context != null && decoderFactory != null && view != null) {
                    view.debug("TilesInitTask.doInBackground");
                    decoder = decoderFactory.make();
                    Point dimensions = decoder.init(context, source);
                    int sWidth = dimensions.x;
                    int sHeight = dimensions.y;
                    int exifOrientation = view.getExifOrientation(context, sourceUri);
                    if (view.sRegion != null) {
                        view.sRegion.left = Math.max(0, view.sRegion.left);
                        view.sRegion.top = Math.max(0, view.sRegion.top);
                        view.sRegion.right = Math.min(sWidth, view.sRegion.right);
                        view.sRegion.bottom = Math.min(sHeight, view.sRegion.bottom);
                        sWidth = view.sRegion.width();
                        sHeight = view.sRegion.height();
                    }
                    return new int[]{sWidth, sHeight, exifOrientation};
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialise bitmap decoder", e);
                this.exception = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(int[] xyo) {
            final ScaleImageView view = viewRef.get();
            if (view != null) {
                if (decoder != null && xyo != null && xyo.length == 3) {
                    view.onTilesInited(decoder, xyo[0], xyo[1], xyo[2]);
                } else if (exception != null && view.onImageEventListener != null) {
                    view.onImageEventListener.onImageLoadError(exception);
                }
            }
        }
    }

    protected static class TileLoadTask extends AsyncTask<Void, Void, Bitmap> {
        protected final WeakReference<ScaleImageView> viewRef;
        protected final WeakReference<ImageRegionDecoder> decoderRef;
        protected final WeakReference<Tile> tileRef;
        protected Exception exception;

        TileLoadTask(ScaleImageView view, ImageRegionDecoder decoder, Tile tile) {
            this.viewRef = new WeakReference<>(view);
            this.decoderRef = new WeakReference<>(decoder);
            this.tileRef = new WeakReference<>(tile);
            tile.loading = true;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try {
                ScaleImageView view = viewRef.get();
                ImageRegionDecoder decoder = decoderRef.get();
                Tile tile = tileRef.get();
                if (decoder != null && tile != null && view != null && decoder.isReady() && tile.visible) {
                    view.debug("TileLoadTask.doInBackground, tile.sRect=%s, tile.sampleSize=%d",
                            tile.sRect,
                            tile.sampleSize);
                    view.decoderLock.readLock().lock();
                    try {
                        if (decoder.isReady()) {
                            // Update tile's file sRect according to rotation
                            view.fileSRect(tile.sRect, tile.fileSRect);
                            if (view.sRegion != null) {
                                tile.fileSRect.offset(view.sRegion.left, view.sRegion.top);
                            }
                            return decoder.decodeRegion(tile.fileSRect, tile.sampleSize);
                        } else {
                            tile.loading = false;
                        }
                    } finally {
                        view.decoderLock.readLock().unlock();
                    }
                } else if (tile != null) {
                    tile.loading = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode tile", e);
                this.exception = e;
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Failed to decode tile - OutOfMemoryError", e);
                this.exception = new RuntimeException(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            final ScaleImageView scaleImageView = viewRef.get();
            final Tile tile = tileRef.get();
            if (scaleImageView != null && tile != null) {
                if (bitmap != null) {
                    tile.bitmap = bitmap;
                    tile.loading = false;
                    scaleImageView.onTileLoaded();
                } else if (exception != null && scaleImageView.onImageEventListener != null) {
                    scaleImageView.onImageEventListener.onTileLoadError(exception);
                }
            }
        }
    }

    protected static class ScaleAndTranslate {
        protected final PointF vTranslate;
        protected float scale;

        protected ScaleAndTranslate(float scale, PointF vTranslate) {
            this.scale = scale;
            this.vTranslate = vTranslate;
        }
    }

    protected static class BitmapLoadTask extends AsyncTask<Void, Void, Integer> {
        protected final WeakReference<ScaleImageView> viewRef;
        protected final WeakReference<Context> contextRef;
        protected final WeakReference<DecoderFactory<? extends ImageDecoder>> decoderFactoryRef;
        protected final Uri source;
        protected final boolean preview;
        protected Bitmap bitmap;
        protected Exception exception;

        BitmapLoadTask(
                ScaleImageView view,
                Context context,
                DecoderFactory<? extends ImageDecoder> decoderFactory,
                Uri source,
                boolean preview) {
            this.viewRef = new WeakReference<>(view);
            this.contextRef = new WeakReference<>(context);
            this.decoderFactoryRef = new WeakReference<DecoderFactory<? extends ImageDecoder>>(decoderFactory);
            this.source = source;
            this.preview = preview;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            try {
                String sourceUri = source.toString();
                Context context = contextRef.get();
                DecoderFactory<? extends ImageDecoder> decoderFactory = decoderFactoryRef.get();
                ScaleImageView view = viewRef.get();
                if (context != null && decoderFactory != null && view != null) {
                    view.debug("BitmapLoadTask.doInBackground");
                    bitmap = decoderFactory.make().decode(context, source);
                    return view.getExifOrientation(context, sourceUri);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load bitmap", e);
                this.exception = e;
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Failed to load bitmap - OutOfMemoryError", e);
                this.exception = new RuntimeException(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Integer orientation) {
            ScaleImageView scaleImageView = viewRef.get();
            if (scaleImageView != null) {
                if (bitmap != null && orientation != null) {
                    if (preview) {
                        scaleImageView.onPreviewLoaded(bitmap);
                    } else {
                        scaleImageView.onImageLoaded(bitmap, orientation, false);
                    }
                } else if (exception != null && scaleImageView.onImageEventListener != null) {
                    if (preview) {
                        scaleImageView.onImageEventListener.onPreviewLoadError(exception);
                    } else {
                        scaleImageView.onImageEventListener.onImageLoadError(exception);
                    }
                }
            }
        }
    }

    protected static class Anim {

        protected float scaleStart; // Scale at start of anim
        protected float scaleEnd; // Scale at end of anim (target)
        protected PointF sCenterStart; // Source center point at start
        protected PointF sCenterEnd; // Source center point at end, adjusted for pan limits
        protected PointF sCenterEndRequested; // Source center point that was requested, without adjustment
        protected PointF vFocusStart; // View point that was double tapped
        protected PointF vFocusEnd; // Where the view focal point should be moved to during the anim
        protected long duration = 500; // How long the anim takes
        protected boolean interruptible = true; // Whether the anim can be interrupted by a touch
        protected int easing = EASE_IN_OUT_QUAD; // Easing style
        protected int origin = ORIGIN_ANIM; // Animation origin (API, double tap or fling)
        protected long time = System.currentTimeMillis(); // Start time
        protected OnAnimationEventListener listener; // Event listener

    }

    protected static class Tile {

        protected Rect sRect;
        protected int sampleSize;
        protected Bitmap bitmap;
        protected boolean loading;
        protected boolean visible;

        // Volatile fields instantiated once then updated before use to reduce GC.
        protected Rect vRect;
        protected Rect fileSRect;

    }
}
