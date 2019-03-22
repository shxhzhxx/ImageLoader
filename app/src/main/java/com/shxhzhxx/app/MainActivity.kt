package com.shxhzhxx.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.shxhzhxx.imageloader.ImageLoader


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val iv = findViewById<ImageView>(R.id.iv)
        val loader = ImageLoader(cacheDir)
        findViewById<View>(R.id.load).setOnClickListener {
            val url = "https://static.usasishu.com/image/2018/09/30/bg-index.jpg"
            val url1 = "https://static.usasishu.com/image/2018/09/30/bg-china-map.png"
            loader.load(iv, url,
                    onComplete = {
                        Log.d("shxhzhxx", "onComplete")
                    },
                    onFailed = {
                        Log.d("shxhzhxx", "onFailed")
                    },
                    onCanceled = {
                        Log.d("shxhzhxx", "onCanceled")
                    })
        }
    }
}
