package com.app.pager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class ProgressView extends View{


    public ProgressView(Context context){
        super(context);
    }

    public ProgressView(Context context,@Nullable AttributeSet attrs){
        super(context,attrs);
    }

    public ProgressView(Context context,@Nullable AttributeSet attrs,int defStyleAttr){
        super(context,attrs,defStyleAttr);
    }

    public ProgressView(Context context,@Nullable AttributeSet attrs,int defStyleAttr,int defStyleRes){
        super(context,attrs,defStyleAttr,defStyleRes);
    }

    // 圆心x坐标
    private int mXCenter;
    // 圆心y坐标
    private int mYCenter;
    // 总进度
    private float progress=0f;
    //扇形半径
    private int arcRadius;
    //背景的半径
    private int bgRectRadius;
    //扇形的颜色
    private int arcRectColor=0xFFFFFFFF;
    //背景的颜色
    private int bgRectColor=0x66666666;

    private final Paint paint=new Paint();
    private final RectF arcRectF=new RectF();
    private final RectF bgRectF=new RectF();

    protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);
        paint.setAntiAlias(true);
        mXCenter=(right-left)/2;
        mYCenter=(bottom-top)/2;
        arcRadius=mXCenter/2;
        bgRectRadius=arcRadius+30;

        arcRectF.set(mXCenter-arcRadius,mYCenter-arcRadius,mXCenter+arcRadius,mYCenter+arcRadius);
        bgRectF.set(mXCenter-bgRectRadius,mYCenter-bgRectRadius,mXCenter+bgRectRadius,mYCenter+bgRectRadius);
    }

    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        paint.setColor(bgRectColor);
        canvas.drawRoundRect(bgRectF,10,10,paint);
        paint.setColor(arcRectColor);
        canvas.drawArc(arcRectF,-90,360*progress,true,paint);
    }
}
