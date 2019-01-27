package com.shxhzhxx.app

import android.os.Build
import android.os.Bundle
import android.widget.ImageView

import androidx.appcompat.app.AppCompatActivity
import com.shxhzhxx.imageloader.ImageLoader
import com.shxhzhxx.imageloader.blurTransformation


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val iv = findViewById<ImageView>(R.id.iv)
        val loader = ImageLoader(cacheDir)
        loader.load(iv, "http://plpwobkse.bkt.clouddn.com/1125-2436-72.png")
    }
}
