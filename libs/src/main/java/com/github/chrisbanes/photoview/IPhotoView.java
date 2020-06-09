package com.github.chrisbanes.photoview;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.View;
import android.widget.ImageView.ScaleType;

public interface IPhotoView{
    PhotoViewAttacher getAttacher();

    ScaleType getScaleType();

    Matrix getImageMatrix();

    void setOnLongClickListener(View.OnLongClickListener l);

    void setOnClickListener(View.OnClickListener l);

    void setScaleType(ScaleType scaleType);

    void setRotationTo(float rotationDegree);

    void setRotationBy(float rotationDegree);

    boolean isZoomable();

    void setZoomable(boolean zoomable);

    RectF getDisplayRect();

    void getDisplayMatrix(Matrix matrix);

    boolean setDisplayMatrix(Matrix finalRectangle);

    void getSuppMatrix(Matrix matrix);

    boolean setSuppMatrix(Matrix matrix);

    float getMinimumScale();

    float getMaximumScale();

    float getScale();

    void setAllowParentInterceptOnEdge(boolean allow);

    void setMinimumScale(float minimumScale);

    void setMaximumScale(float maximumScale);

    void setScaleLevels(float minimumScale,float maximumScale);

    void setOnMatrixChangeListener(OnMatrixChangedListener listener);

    void setOnPhotoTapListener(OnPhotoTapListener listener);

    void setOnOutsidePhotoTapListener(OnOutsidePhotoTapListener listener);

    void setOnViewTapListener(OnViewTapListener listener);

    void setOnViewDragListener(OnViewDragListener listener);

    void setScale(float scale);

    void setScale(float scale,boolean animate);

    void setScale(float scale,float focalX,float focalY,boolean animate);

    void setZoomTransitionDuration(int milliseconds);

    void setOnDoubleTapListener(GestureDetector.OnDoubleTapListener onDoubleTapListener);

    void setOnScaleChangeListener(OnScaleChangedListener onScaleChangedListener);

    void setOnSingleFlingListener(OnSingleFlingListener onSingleFlingListener);
}
