package com.app.pager;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.scale.image.ImageSource;
import com.scale.image.ScaleImageView;

import java.util.ArrayList;

public class ImageActivity extends AppCompatActivity {
    private ImageWatcher imageWatcher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        imageWatcher = findViewById(R.id.image_watcher);
        imageWatcher.setImageLoader(new ImageWatcher.ImageLoader() {
            @Override
            public void loadImage(final ScaleImageView imageView, String path, int position) {
                if (position % 2 == 0) {
                    Glide.with(imageView.getContext()).load("file:///android_asset/abd.gif").into(imageView);
//                    Glide.with(imageView.getContext())
//                         .asFile()
//                         .load("http://s1.dgtle.com/dgtle_img/ins-comment/2020/05/28"+"/e5dda202005281257525499.jpeg")
//                         .override(Target.SIZE_ORIGINAL)
//                         .listener(new RequestListener<File>(){
//                             @Override
//                             public boolean onLoadFailed(
//                                     @Nullable GlideException e,
//                                     Object model,
//                                     Target<File> target,
//                                     boolean isFirstResource)
//                             {
//                                 return false;
//                             }
//
//                             @Override
//                             public boolean onResourceReady(
//                                     File resource,
//                                     Object model,
//                                     Target<File> target,
//                                     DataSource dataSource,
//                                     boolean isFirstResource)
//                             {
//                                 imageView.setImage(ImageSource.uri(resource.getAbsolutePath()));
//                                 imageView.requestLayoutAndroidScaleCenter();
//                                 return false;
//                             }
//                         })
//                         .submit();
                } else {
                    imageView.setImage(ImageSource.asset("WechatIMG18632.jpeg"));
                }
            }
        });
        ArrayList<String> datas = new ArrayList<>();
        datas.add("");
        datas.add("");
        datas.add("");
        datas.add("");
        datas.add("");
        imageWatcher.setDatas(datas, 1);
        imageWatcher.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ImageActivity.this, "长按了", Toast.LENGTH_SHORT).show();
                return false;
            }
        });
        imageWatcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(ImageActivity.this, "单击", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (imageWatcher.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }
}
