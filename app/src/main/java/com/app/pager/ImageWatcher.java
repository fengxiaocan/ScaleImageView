package com.app.pager;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.scale.image.ScaleImageView;

import java.util.ArrayList;

public class ImageWatcher extends FrameLayout implements ViewPager.OnPageChangeListener{
    private static SparseArray<ViewState> transferViews;
    private static final int MATCH_PARENT=ViewGroup.LayoutParams.MATCH_PARENT;


    public static final void addSparseImage(ImageView... iImage){
        if(iImage!=null){
            transferViews=new SparseArray<>();
            for(int i=0;i<iImage.length;i++){
                if(iImage[i] != null){
                    transferViews.put(i,ViewState.from(iImage[i]));
                }
            }
        }
    }

    protected static final int TOUCH_MODE_NONE=0; // 无状态
    protected static final int TOUCH_MODE_DOWN=1; // 单点按下
    protected static final int TOUCH_MODE_DRAG=3; // 单点拖拽
    // 拖拽的最大距离,大于这个距离就不再缩放
    protected static final int DRAG_MAX_DISTANCE=Resources.getSystem().getDisplayMetrics().widthPixels/2;
    // 拖拽缩放的最小值
    protected static final float EXIT_MIN_SCALE=0.6f;//缩放到一定量就退出
    protected static final float DRAG_MIN_SCALE=0.5f;

    private volatile boolean isFirst=true;
    private ViewPager viewPager;
    private SparseArray<View> imageArray=new SparseArray<>();
    private ArrayList<String> mDatas;
    private OnExitListener onExitListener;
    private ImageLoader imageLoader;
    private int startPosition;//一开始的选中图标

    public ImageWatcher(@NonNull Context context){
        super(context);
    }

    public ImageWatcher(@NonNull Context context,@Nullable AttributeSet attrs){
        super(context,attrs);
    }

    public ImageWatcher(@NonNull Context context,@Nullable AttributeSet attrs,int defStyleAttr){
        super(context,attrs,defStyleAttr);
    }

    {
        viewPager=new ViewPager(getContext());
        addView(viewPager);
        viewPager.addOnPageChangeListener(this);
    }

    protected ValueAnimator dragAnimator;
    protected ValueAnimator enterAnimator;
    protected ValueAnimator exitAnimator;
    protected View currentView;
    protected int mTouchMode=TOUCH_MODE_NONE;
    protected float downX, downY;
    protected float moveX, moveY;
    protected float dragScale=1f;//拖拽缩放值

    protected static boolean isRunningAnim(ValueAnimator animator){
        return animator!=null&&animator.isRunning();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event){
        if(isRunningAnim(dragAnimator)||isRunningAnim(enterAnimator)||isRunningAnim(exitAnimator)){
            return true;
        }

        int action=event.getAction();
        switch(action){
            //只有单指触碰下才会回调Move
            case MotionEvent.ACTION_DOWN:
                mTouchMode=TOUCH_MODE_DOWN;
                moveX=downX=event.getX();
                moveY=downY=event.getY();
                if(currentView==null){
                    currentView=imageArray.get(viewPager.getCurrentItem());
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mTouchMode=TOUCH_MODE_NONE;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(mTouchMode==TOUCH_MODE_DRAG||(currentView!=null&&currentView.getScaleX()<1)){
                    //判断拖拽结束的状态
                    dispatchDragViewResult();
                }
                mTouchMode=TOUCH_MODE_NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if(mTouchMode==TOUCH_MODE_DRAG){
                    //拦截所有子类事件
                    moveX=event.getX();
                    moveY=event.getY();
                    dispatchDragEvent();
                    return true;
                }
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event){
        int action=event.getAction();
        switch(action){
            //只有单指触碰下才会回调Move
            case MotionEvent.ACTION_MOVE:

                if(currentView!=null){
                    if(mTouchMode==TOUCH_MODE_DOWN){
                        float vX=event.getX()-moveX;
                        float vY=event.getY()-moveY;
                        float absX=Math.abs(vX);
                        float absY=Math.abs(vY);
                        if(absY>absX&&vY>0&&absY >= absX*1.5){
                            //上下距离滑动了
                            mTouchMode=TOUCH_MODE_DRAG;
                            //处理拖拽事件
                            moveX=event.getX();
                            moveY=event.getY();
                            dispatchDragEvent();
                            return true;
                        }
                    } else if(mTouchMode==TOUCH_MODE_DRAG){
                        //拦截所有子类事件
                        moveX=event.getX();
                        moveY=event.getY();
                        dispatchDragEvent();
                        return true;
                    } else{
                        moveX=event.getX();
                        moveY=event.getY();
                    }
                }
                break;

        }
        return super.onInterceptTouchEvent(event);
    }

    /**
     * 处理拖拽事件
     */
    protected void dispatchDragEvent(){
        if(moveY>downY){
            //向下拖拽需要缩小
            final float distance=moveY-downY;
            if(distance >= DRAG_MAX_DISTANCE){
                dragScale=DRAG_MIN_SCALE;
            } else{
                dragScale=1-(1-DRAG_MIN_SCALE)*distance/DRAG_MAX_DISTANCE;
            }
        } else{
            dragScale=1.0f;
        }
        dispatchDragView();
    }

    /**
     * 处理拖拽移动
     */
    protected void dispatchDragView(){
        currentView.setScaleX(dragScale);
        currentView.setScaleY(dragScale);
        currentView.setTranslationX((moveX-downX)/dragScale/2);
        currentView.setTranslationY((moveY-downY)/dragScale/2);

        //设置背景颜色
        setBackgroundColor(ViewState.alphaWatcherColor(dragScale));
    }

    /**
     * 处理释放拖拽结果
     */
    protected void dispatchDragViewResult(){
        if(dragAnimator!=null&&dragAnimator.isRunning()){
            dragAnimator.cancel();
        }
        if(currentView.getScaleX()<EXIT_MIN_SCALE){
            onExitByAnim();
        } else{
            dragAnimator=ViewState.dragByAnim(this,currentView);
            dragAnimator.addListener(new AnimatorListenerAdapter(){
                @Override
                public void onAnimationEnd(Animator animation){
                    dragAnimator=null;
                }
            });
            dragAnimator.start();
        }
    }

    private void onExitByAnim(){
        ViewState state=null;
        if(transferViews!=null){
            state=transferViews.get(viewPager.getCurrentItem());
        }
        if(state==null){
            exitAnimator=ViewState.exitByAnim(this,currentView);
        } else{
            exitAnimator=ViewState.exitByAnim(this,currentView,state);
        }
        exitAnimator.addListener(new AnimatorListenerAdapter(){
            @Override
            public void onAnimationEnd(Animator animation){
                if(onExitListener!=null){
                    onExitListener.onExit();
                } else{
                    ((Activity)getContext()).finish();
                }
                recycler();
                exitAnimator=null;
            }
        });
        exitAnimator.start();
    }

    public boolean onBackPressed(){
        if(mDatas!=null&&mDatas.size()>0){
            if(!isRunningAnim(exitAnimator)){
                onExitByAnim();
            }
            return true;
        } else{
            return false;
        }
    }

    public String getCurrentData(){
        if(mDatas!=null){
            return mDatas.get(viewPager.getCurrentItem());
        }
        return null;
    }

    @Override
    public void onPageScrolled(int position,float positionOffset,int positionOffsetPixels){
    }

    @Override
    public void onPageSelected(int position){
        currentView=imageArray.get(position);
    }

    @Override
    public void onPageScrollStateChanged(int state){

    }

    /**
     * 设置数据
     */
    public void setDatas(ArrayList<String> datas,int position){
        startPosition=position;
        mDatas=datas;
        imageArray.clear();
        viewPager.setAdapter(new ImagePagerAdapter());
        viewPager.setCurrentItem(position);
    }

    public void setOnExitListener(OnExitListener onExitListener){
        this.onExitListener=onExitListener;
    }

    public void setImageLoader(ImageLoader imageLoader){
        this.imageLoader=imageLoader;
    }

    class ImagePagerAdapter extends PagerAdapter{

        @Override
        public int getCount(){
            if(mDatas!=null){
                return mDatas.size();
            }
            return 0;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view,@NonNull Object object){
            return view==object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container,int position){
            FrameLayout frameLayout=new FrameLayout(container.getContext());
            int matchParent=ViewGroup.LayoutParams.MATCH_PARENT;
            container.addView(frameLayout,new ViewGroup.LayoutParams(matchParent,matchParent));
            //创建图片
            disposeImageView(frameLayout,position);
            return frameLayout;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container,int position,@NonNull Object object){
            imageArray.remove(position);
            container.removeView((View)object);
        }

        private ScaleImageView disposeImageView(FrameLayout frameLayout,int position){
            final ScaleImageView imageView=new ScaleImageView(frameLayout.getContext());
            frameLayout.addView(imageView,new LayoutParams(MATCH_PARENT,MATCH_PARENT));
            imageView.setOnClickListener(new OnClickListener(){
                @Override
                public void onClick(View v){
                    ImageWatcher.this.performClick();
                }
            });
            imageView.setOnLongClickListener(new OnLongClickListener(){
                @Override
                public boolean onLongClick(View v){
                    return ImageWatcher.this.performLongClick();
                }
            });
            imageView.setOnImageEventListener(new ScaleImageView.DefaultOnImageEventListener(){
                @Override
                public void onReady(){
                    Log.e("noah","onReady");
                }

                @Override
                public void onImageLoaded(){
                    Log.e("noah","onImageLoaded");
                }
            });
            imageLoader.loadImage(imageView,mDatas.get(position),position);
            imageArray.put(position,imageView);
            return imageView;
        }
    }

    @Override
    protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);
        if(isFirst&&mDatas!=null&&mDatas.size()>0){
            isFirst=false;
            currentView=imageArray.get(viewPager.getCurrentItem());
            ViewState state=null;
            if(transferViews!=null){
                state=transferViews.get(viewPager.getCurrentItem());
            }
            if(state==null){
                enterAnimator=ViewState.enterByAnim(this,currentView);
            } else{
                enterAnimator=ViewState.enterByAnim(this,currentView,state);
            }
            enterAnimator.addListener(new AnimatorListenerAdapter(){
                @Override
                public void onAnimationCancel(Animator animation){
                    enterAnimator=null;
                }

                @Override
                public void onAnimationEnd(Animator animation){
                    enterAnimator=null;
                }
            });
            enterAnimator.start();
        }
    }

    public void recycler(){
        if(transferViews!=null){
            transferViews.clear();
        }
        transferViews=null;
        imageArray.clear();
    }

    public interface OnExitListener{
        void onExit();
    }

    public interface ImageLoader{
        void loadImage(ScaleImageView imageView,String path,int position);
    }
}
