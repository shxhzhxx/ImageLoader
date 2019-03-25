package com.shxhzhxx.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.shxhzhxx.imageloader.ImageLoader


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val iv1 = findViewById<ImageView>(R.id.iv1)
        val iv2 = findViewById<ImageView>(R.id.iv2)
        val iv3 = findViewById<ImageView>(R.id.iv3)
        val iv4 = findViewById<ImageView>(R.id.iv4)
        val iv5 = findViewById<ImageView>(R.id.iv5)
        val u1 = "https://static.usasishu.com/image/2018/09/30/bg-index.jpg"
        val u2 = "https://static.usasishu.com/image/2018/09/30/bg-china-map.png"
        val u3 = "https://static.usasishu.com/image/2018/10/12/how_to_learn_banner.png"
        val u4 = "https://static.usasishu.com/image/2018/10/12/time_plan_bg.png"
        val u5 = "https://static.usasishu.com/image/2018/10/15/0001.png"
        val loader = ImageLoader(cacheDir)
        Log.d(TAG, "cores:${Runtime.getRuntime().availableProcessors()}")
        findViewById<View>(R.id.load).setOnClickListener {
            loader.load(iv1, u1, onCanceled = { Log.d(TAG, "onCanceled 1") })
            loader.load(iv2, u2, onCanceled = { Log.d(TAG, "onCanceled 2") })
            loader.load(iv3, u3, onCanceled = { Log.d(TAG, "onCanceled 3") })
            loader.load(iv4, u4, onCanceled = { Log.d(TAG, "onCanceled 4") })
            loader.load(iv5, u5, onCanceled = { Log.d(TAG, "onCanceled 5") })
//            Handler().postDelayed({ finish() }, 200)
        }
    }
}
