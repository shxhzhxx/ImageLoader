package com.shxhzhxx.app

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.shxhzhxx.imageloader.ImageLoader
import kotlinx.android.synthetic.main.activity_main.*


private const val TAG = "MainActivity"
const val u1 = "https://static.usasishu.com/image/2018/09/30/bg-index.jpg"
const val u2 = "https://static.usasishu.com/image/2018/09/30/bg-china-map.png"
const val u3 = "https://static.usasishu.com/image/2018/10/12/how_to_learn_banner.png"
const val u4 = "https://static.usasishu.com/image/2018/10/12/time_plan_bg.png"
const val u5 = "https://static.usasishu.com/image/2018/10/15/0001.png"

class MainActivity : AppCompatActivity() {
    private val loader by lazy { ImageLoader(contentResolver, cacheDir) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        load.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        loader.load(iv, "https://static.usasishu.com/20190620_172316.jpg", centerCrop = false, roundingRadius = 30,
                onLoad = {
                    Log.d(TAG, "onLoad")
                },
                onFailure = {
                    Log.d(TAG, "onFailure")
                },
                onCancel = {
                    Log.d(TAG, "onCancel")
                }
        )
    }
}
