package com.app.pager;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.scale.image.ScaleImageView;

import java.io.Serializable;

public class ViewState implements Serializable{

    public static final int WATCHER_COLOR=Color.BLACK;//图片浏览器的颜色值
    public static final int ENTER_ANIMATOR_TIME=250;//进场动画时间
    public static final int EXIT_ANIMATOR_TIME=250;//退场动画时间
    public static final int DRAG_ANIMATOR_TIME=250;//拖拽结束动画时间

    private static final int WATCHER_RED=Color.red(WATCHER_COLOR);
    private static final int WATCHER_GREEN=Color.green(WATCHER_COLOR);
    private static final int WATCHER_BLUE=Color.blue(WATCHER_COLOR);

    protected int width;
    protected int height;
    protected float screenX;//在屏幕的坐标
    protected float screenY;//在屏幕的坐标

    public static ViewState from(ImageView view){
        ViewState vs=new ViewState();
        vs.width=view.getMeasuredWidth();
        vs.height=view.getMeasuredHeight();
        int[] outLocation=new int[2];
        view.getLocationOnScreen(outLocation);
        vs.screenX=outLocation[0];
        vs.screenY=outLocation[1];
        return vs;
    }

    public static ValueAnimator enterByAnim(final ViewGroup parent,final View view,ViewState state){
        ValueAnimator animator=ValueAnimator.ofFloat(0,1).setDuration(ENTER_ANIMATOR_TIME);
        int[] location=new int[2];
        parent.getLocationOnScreen(location);
        final int currentX=location[0];
        final int currentY=location[1];
        final int viewWidth=view.getMeasuredWidth();
        final int viewHeight=view.getMeasuredHeight();

        final float screenX=state.screenX;
        final float screenY=state.screenY;
        //需要偏移的目标量Y
        final float orX=view.getX();
        final float dX=screenX-view.getX()-currentX;
        //需要偏移的目标量Y
        final float orY=view.getY();
        final float dY=screenY-view.getY()-currentY;

        final int sWidth=state.width;
        final int sHeight=state.height;
        view.setX(dX);
        view.setY(dY);
        ViewGroup.LayoutParams params=view.getLayoutParams();
        params.width=state.width;
        params.height=state.height;
        if(view instanceof ScaleImageView){
            ((ScaleImageView)view).requestLayoutAndroidScaleCenter();
        } else{
            view.requestLayout();
        }
        //设置背景颜色
        parent.setBackgroundColor(alphaWatcherColor(0));

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override
            public void onAnimationUpdate(ValueAnimator animation){
                float value=(float)animation.getAnimatedValue();
                float vX=dX+(orX-dX)*value;
                float vY=dY+(orY-dY)*value;
                view.setX(vX);
                view.setY(vY);
                ViewGroup.LayoutParams params=view.getLayoutParams();
                params.width=(int)(sWidth+(viewWidth-sWidth)*value);
                params.height=(int)(sHeight+(viewHeight-sHeight)*value);
                if(view instanceof ScaleImageView){
                    ((ScaleImageView)view).requestLayoutAndroidScaleCenter();
                } else{
                    view.requestLayout();
                }
                //设置背景颜色
                parent.setBackgroundColor(alphaWatcherColor(value));
            }
        });
        return animator;
    }

    public static ValueAnimator enterByAnim(final ViewGroup parent,final View view){
        ValueAnimator animator=ValueAnimator.ofFloat(0,1).setDuration(ENTER_ANIMATOR_TIME);

        view.setScaleX(0.001f);
        view.setScaleY(0.001f);
        //设置背景颜色
        parent.setBackgroundColor(alphaWatcherColor(0));

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override
            public void onAnimationUpdate(ValueAnimator animation){
                float value=(float)animation.getAnimatedValue();
                if(value>0.001f){
                    view.setScaleX(value);
                    view.setScaleY(value);
                }
                //设置背景颜色
                parent.setBackgroundColor(alphaWatcherColor(value));
            }
        });
        return animator;
    }


    public static ValueAnimator exitByAnim(final ViewGroup parent,final View view,ViewState state){
        ValueAnimator animator=ValueAnimator.ofFloat(0,1).setDuration(EXIT_ANIMATOR_TIME);
        int[] location=new int[2];
        parent.getLocationOnScreen(location);
        final int parentCurrentX=location[0];
        final int parentCurrentY=location[1];

        final float screenX=state.screenX;
        final float screenY=state.screenY;

        final int sWidth=state.width;
        final int sHeight=state.height;

        final int viewWidth=view.getMeasuredWidth();
        final int viewHeight=view.getMeasuredHeight();
        final float scaleX=view.getScaleX();
        final float scaleY=view.getScaleY();
        final float translationX=view.getTranslationX();
        final float translationY=view.getTranslationY();

        //x坐标,需要减去偏移的量
        float viewX=view.getX()-translationX;
        final float orderX=screenX-viewX-parentCurrentX;
        //Y坐标,需要减去偏移的量
        float viewY=view.getY()-translationY;
        final float orderY=screenY-viewY-parentCurrentY;

        view.setTranslationX(0);
        view.setTranslationY(0);
        view.setScaleX(1f);
        view.setScaleY(1f);

        ViewGroup.LayoutParams params=view.getLayoutParams();
        final float currentWidth=viewWidth*scaleX;
        params.width=(int)currentWidth;
        final float currentHeight=viewHeight*scaleY;
        params.height=(int)currentHeight;

        final float fromX=translationX+(viewWidth-currentWidth)/2;
        view.setX(fromX);
        final float fromY=translationY+(viewHeight-currentHeight)/2;
        view.setY(fromY);

        if(view instanceof ScaleImageView){
            ((ScaleImageView)view).requestLayoutAndroidScaleCenter();
        } else{
            view.requestLayout();
        }

        //设置背景颜色
        parent.setBackgroundColor(alphaWatcherColor(scaleX));

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override
            public void onAnimationUpdate(ValueAnimator animation){
                float value=(float)animation.getAnimatedValue();
                float vX=fromX-(fromX-orderX)*value;
                float vY=fromY-(fromY-orderY)*value;
                view.setX(vX);
                view.setY(vY);
                ViewGroup.LayoutParams params=view.getLayoutParams();
                params.width=(int)(currentWidth-(currentWidth-sWidth)*value);
                params.height=(int)(currentHeight-(currentHeight-sHeight)*value);

                if(view instanceof ScaleImageView){
                    ((ScaleImageView)view).requestLayoutAndroidScaleCenter();
                } else{
                    view.requestLayout();
                }

                //设置背景颜色
                parent.setBackgroundColor(alphaWatcherColor(scaleX-scaleX*value));
            }
        });
        return animator;
    }

    public static ValueAnimator exitByAnim(final ViewGroup parent,final View view){
        ValueAnimator animator=ValueAnimator.ofFloat(0,1).setDuration(EXIT_ANIMATOR_TIME);

        final float scaleX=view.getScaleX();
        final float scaleY=view.getScaleY();
        final float translationX=view.getTranslationX();
        final float translationY=view.getTranslationY();

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override
            public void onAnimationUpdate(ValueAnimator animation){
                float value=(float)animation.getAnimatedValue();

                view.setScaleX(scaleX-scaleX*value);
                view.setScaleY(scaleY-scaleY*value);

                view.setTranslationX(translationX-translationX*value);
                view.setTranslationY(translationY-translationY*value);

                //设置背景颜色
                parent.setBackgroundColor(alphaWatcherColor(scaleX-scaleX*value));
            }
        });
        return animator;
    }

    /**
     * 拖拽结束动画
     *
     * @param view
     * @return
     */
    public static ValueAnimator dragByAnim(final ViewGroup parent,final View view){
        ValueAnimator valueAnimator=ValueAnimator.ofFloat(0,1);
        valueAnimator.setDuration(DRAG_ANIMATOR_TIME);
        final float lastScale=view.getScaleX();
        final float lastX=view.getTranslationX();
        final float lastY=view.getTranslationY();

        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override
            public void onAnimationUpdate(ValueAnimator animation){
                float value=(float)animation.getAnimatedValue();
                float vScale=lastScale+(1-lastScale)*value;
                view.setScaleX(vScale);
                view.setScaleY(vScale);
                float tranX=lastX-lastX*value;
                float tranY=lastY-lastY*value;
                view.setTranslationX(tranX);
                view.setTranslationY(tranY);
                //设置背景颜色
                parent.setBackgroundColor(alphaWatcherColor(lastScale+(1-lastScale)*value));
            }
        });
        return valueAnimator;
    }

    public static int alphaWatcherColor(float alpha){
        return Color.argb((int)(alpha*255),WATCHER_RED,WATCHER_GREEN,WATCHER_BLUE);
    }
}
