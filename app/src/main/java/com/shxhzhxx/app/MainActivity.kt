package com.shxhzhxx.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shxhzhxx.imageloader.ImageLoader
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_main.view.*


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

        val adapter = MyAdapter(loader)
        list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        list.adapter = adapter
        swipe.setOnRefreshListener {
            adapter.notifyDataSetChanged()
            swipe.isRefreshing = false
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
