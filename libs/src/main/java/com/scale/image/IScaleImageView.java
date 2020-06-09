package com.scale.image;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.scale.image.decoder.DecoderFactory;
import com.scale.image.decoder.ImageDecoder;
import com.scale.image.decoder.ImageRegionDecoder;

import java.util.concurrent.Executor;

public interface IScaleImageView{

    void setOrientation(int orientation);

    void setImage(@NonNull ImageSource imageSource);

    void setImage(@NonNull ImageSource imageSource,ImageViewState state);

    void setImage(@NonNull ImageSource imageSource,ImageSource previewSource);

    void setImage(@NonNull ImageSource imageSource,ImageSource previewSource,ImageViewState state);


    void setMaxTileSize(int maxPixels);

    void setMaxTileSize(int maxPixelsX,int maxPixelsY);

    void recycle();

    void viewToFileRect(Rect vRect,Rect fRect);

    void visibleFileRect(Rect fRect);

    @Nullable
    PointF viewToSourceCoord(PointF vxy);

    @Nullable
    PointF viewToSourceCoord(float vx,float vy);

    @Nullable
    PointF viewToSourceCoord(PointF vxy,@NonNull PointF sTarget);

    @Nullable
    PointF viewToSourceCoord(float vx,float vy,@NonNull PointF sTarget);

    @Nullable
    PointF sourceToViewCoord(PointF sxy);

    @Nullable
    PointF sourceToViewCoord(float sx,float sy);

    @Nullable
    PointF sourceToViewCoord(PointF sxy,@NonNull PointF vTarget);

    @Nullable
    PointF sourceToViewCoord(float sx,float sy,@NonNull PointF vTarget);


    void setRegionDecoderClass(@NonNull Class<? extends ImageRegionDecoder> regionDecoderClass);

    void setRegionDecoderFactory(@NonNull DecoderFactory<? extends ImageRegionDecoder> regionDecoderFactory);

    void setBitmapDecoderClass(@NonNull Class<? extends ImageDecoder> bitmapDecoderClass);

    void setBitmapDecoderFactory(@NonNull DecoderFactory<? extends ImageDecoder> bitmapDecoderFactory);

    void getPanRemaining(RectF vTarget);

    void setPanLimit(int panLimit);

    void setMinimumScaleType(int scaleType);

    void setMaxScale(float maxScale);

    void setMinScale(float minScale);

    void setMinimumDpi(int dpi);

    void setMaximumDpi(int dpi);

    float getMaxScale();

    float getMinScale();

    void setMinimumTileDpi(int minimumTileDpi);

    @Nullable
    PointF getCenter();

    float getScale();

    void setScaleAndCenter(float scale,@Nullable PointF sCenter);

    void resetScaleAndCenter();

    boolean isReady();

    boolean isImageLoaded();

    int getSWidth();

    int getSHeight();

    int getOrientation();

    int getAppliedOrientation();

    @Nullable
    ImageViewState getState();

    boolean isZoomEnabled();

    void setZoomEnabled(boolean zoomEnabled);

    boolean isQuickScaleEnabled();

    void setQuickScaleEnabled(boolean quickScaleEnabled);

    boolean isPanEnabled();

    void setPanEnabled(boolean panEnabled);

    void setTileBackgroundColor(int tileBgColor);

    void setDoubleTapZoomScale(float doubleTapZoomScale);

    void setDoubleTapZoomDpi(int dpi);

    void setDoubleTapZoomStyle(int doubleTapZoomStyle);

    void setDoubleTapZoomDuration(int durationMs);

    void setExecutor(@NonNull Executor executor);

    void setEagerLoadingEnabled(boolean eagerLoadingEnabled);

    void setDebug(boolean debug);

    boolean hasImage();

    void setOnImageEventListener(ScaleImageView.OnImageEventListener onImageEventListener);

    void setOnStateChangedListener(ScaleImageView.OnStateChangedListener onStateChangedListener);

    @Nullable
    ScaleImageView.AnimationBuilder animateCenter(PointF sCenter);

    @Nullable
    ScaleImageView.AnimationBuilder animateScale(float scale);

    @Nullable
    ScaleImageView.AnimationBuilder animateScaleAndCenter(float scale,PointF sCenter);
}
