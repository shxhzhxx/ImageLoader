package com.shxhzhxx.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.shxhzhxx.imageloader.ImageLoader
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_main.view.iv


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
        loader.cancelAll()
        setContentView(R.layout.activity_main)

        val url = "https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1565784417647&di=9c682c309d855f51d092c3fc8be194a5&imgtype=0&src=http%3A%2F%2Fpic33.nipic.com%2F20131007%2F13639685_123501617185_2.jpg"
        iv.setOnClickListener {
            println("width:${iv.width}")
            println("height:${iv.height}")
            loader.load(iv,url,centerCrop = false)
        }
    }
}

class MyAdapter(private val loader: ImageLoader) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_main, parent, false)) {}

    override fun getItemCount() = 20

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        println("$position  load   ${holder.itemView.iv.hashCode()}")
        loader.load(holder.itemView.iv, u1, onLoad = {
            println("$position  onLoad")
        })
    }
}
