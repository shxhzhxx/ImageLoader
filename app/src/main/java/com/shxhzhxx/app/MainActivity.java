package com.shxhzhxx.app;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.shxhzhxx.imageloader.ImageLoader;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final String URL = "https://image.yizhujiao.com/FiZr1lFxhobKLogy4pkTfLqv6xrV";
    ImageView iv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageLoader.init(this);

        iv = findViewById(R.id.iv);
        iv.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Log.d(TAG, "onClick");

        ImageLoader.getInstance().getUrlLoader().clearCache(URL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            ImageLoader.getInstance().getBitmapLoader().clearMemCache();
        }
        ImageLoader.getInstance().load(URL).withoutClear().callback(new ImageLoader.Callback() {
            @Override
            public void onComplete() {
                Log.d(TAG, "onComplete");
            }

            @Override
            public void onFailed() {
                Log.d(TAG, "onFailed");
            }

            @Override
            public void onCanceled() {
                Log.d(TAG, "onCanceled");
            }
        }).into(iv);
    }
}
