package com.shxhzhxx.app

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Movie
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Bundle
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
            loader.bitmapLoader.urlLoader.load("http://img.mp.itc.cn/upload/20170110/b56b87be5f174eca81f9d06116aa1968.jpg", onLoad = {
                val movie = Movie.decodeFile(it.absolutePath)
                iv.setImageDrawable(object : Drawable() {
                    override fun draw(canvas: Canvas) {
                        movie.draw(canvas, 0f, 0f)
                    }

                    override fun setAlpha(alpha: Int) {

                    }

                    override fun getOpacity(): Int {
                        return PixelFormat.TRANSLUCENT
                    }

                    override fun setColorFilter(colorFilter: ColorFilter?) {
                    }

                })
            })
        }
    }
}
