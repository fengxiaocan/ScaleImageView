# ScaleImageView
1.结合[PhotoView](https://github.com/chrisbanes/PhotoView)跟[subsampling-scale-image-view](https://github.com/davemorrissey/subsampling-scale-image-view),可以使用该View加载普通Gif图片,或者加载超大型图片


说明:使用PhotoView的方式展示图片不会使用加载大图的效果,要加载大图效果使用setImage()方法

使用:

1.如果直接使用ImageView的setImageBitmap等方法设置的图片,则为photoView 的加载方式;

2.如果使用的是subsampling-scale-image-view的加载方式setImage(),则使用subsampling-scale-image-view控件来加载;

3.修改了PhotoView的双击缩放,不支持中等缩放,要么直接缩小,要么直接放大,跟subsampling-scale-image-view一致;

4.为了区分subsampling-scale-image-view跟PhotoView的使用方式,可以把控件强转为IPhotoView跟IScaleImageView,则可以使用各自的方法;

5.如果直接使用Glide.into(ScaleImageView)的方式,直接走PhotoView的加载方式,对超大图片无效;

6.在xml中使用
    
      <com.scale.image.ScaleImageView
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
        
7.在代码中使用

      ScaleImageView imageView=new ScaleImageView(getContext());
      imageView.setImage(ImageSource.asset("abf.jpg"));
      or:Glide.with(context).asGif().load("file:///android_asset/abd.gif").into(imageView);
      

依赖:

Step 1. Add the JitPack repository to your build file gradle

Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
	
Step 2. Add the dependency

	dependencies {
	        implementation 'com.github.fengxiaocan:ScaleImageView:v1.0.1'
	}