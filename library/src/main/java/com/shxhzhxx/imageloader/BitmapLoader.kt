package com.shxhzhxx.imageloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import com.shxhzhxx.urlloader.TaskManager
import com.shxhzhxx.urlloader.UrlLoader
import java.io.File
import java.io.IOException

private const val TAG = "BitmapLoader"
private const val MEM_SCALE = 1024

class Callback(
        val onComplete: ((Bitmap) -> Unit)? = null,
        val onFailed: (() -> Unit)? = null,
        val onCanceled: (() -> Unit)? = null
)

class BitmapLoader(fileCachePath: File) : TaskManager<Callback>() {
    val urlLoader = UrlLoader(fileCachePath, 50 * 1024 * 1024)
    private val memoryCache = object : LruCache<Params, Bitmap>((Math.max(1, Runtime.getRuntime().maxMemory() / MEM_SCALE / 8)).toInt()) {
        override fun sizeOf(key: Params, value: Bitmap) = value.byteCount / MEM_SCALE
    }

    @JvmOverloads
    fun load(path: String, @IntRange(from = 0) width: Int = 0, @IntRange(from = 0) height: Int = 0, centerCrop: Boolean = true, tag: Any? = null,
             onComplete: ((Bitmap) -> Unit)? = null,
             onFailed: (() -> Unit)? = null,
             onCanceled: (() -> Unit)? = null): Int {
        val params = Params(path, width, height, centerCrop)
        return start(params, { Worker(params) }, tag, Callback(onComplete, onFailed, onCanceled)).also { id ->
            if (id < 0) {
                onFailed?.invoke()
            }
        }
    }

    fun syncLoad(path: String, @IntRange(from = 0) width: Int = 0, @IntRange(from = 0) height: Int = 0, centerCrop: Boolean = true): Bitmap? {
        val params = Params(path, width, height, centerCrop)
        return memoryCache[params] ?: kotlin.run {
            val f = File(path)
            return@run if (f.exists()) f else urlLoader.syncLoad(path)
        }?.decodeBitmap(params)?.also { memoryCache.put(params, it) }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun resizeMemCache(@IntRange(from = 1) maxSize: Int) {
        memoryCache.resize(maxSize)
    }

    fun clearMemCache() {
        memoryCache.evictAll()
    }

    fun trimMemory(resizeCache: Boolean) {
        val size = memoryCache.size()
        when {
            size < 2 -> return
            resizeCache && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> resizeMemCache(size / 2)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 -> memoryCache.trimToSize(size / 2)
            else -> clearMemCache()
        }
    }

    internal inner class Worker(private val params: Params) : Task(params) {
        override fun doInBackground() {
            if (isCanceled)
                return
            val bitmap = syncLoad(params.path, params.width, params.height, params.centerCrop)
            postResult = if (bitmap != null) Runnable {
                observers.forEach { it?.onComplete?.invoke(bitmap) }
            } else Runnable {
                observers.forEach { it?.onFailed?.invoke() }
            }
        }

        override fun onObserverUnregistered(observer: Callback?) {
            observer?.onCanceled?.invoke()
        }

        override fun onCanceled() {
            observers.forEach { it?.onCanceled?.invoke() }
        }
    }
}

data class Params(val path: String, val width: Int, val height: Int, val centerCrop: Boolean)

private fun File.decodeBitmap(params: Params): Bitmap? {
    val centerCrop = params.centerCrop && params.height > 0 && params.width > 0
    if (params.height <= 0 && params.width <= 0) {
        Log.e(TAG, "load bitmap without compress :$absolutePath")
    }
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(absolutePath, opts)
    val (height, width) = listOf(params.height, params.width).map { return@map if (it <= 0) Int.MAX_VALUE else it }
    val (out, dst) = listOf(opts.outHeight to height, opts.outWidth to width)
            .run { return@run if (centerCrop) minBy { it.first / it.second } else maxBy { it.first / it.second } }!!
    opts.inSampleSize = out / dst
    opts.inDensity = out
    opts.inTargetDensity = dst * opts.inSampleSize
    opts.inScaled = true
    opts.inJustDecodeBounds = false

    return if (!centerCrop) BitmapFactory.decodeFile(absolutePath, opts) else
        try {
            BitmapRegionDecoder.newInstance(absolutePath, !canWrite()).decodeRegion(Rect(
                    opts.outWidth / 2 - width * opts.inSampleSize / 2,
                    opts.outHeight / 2 - height * opts.inSampleSize / 2,
                    opts.outWidth / 2 + width * opts.inSampleSize / 2,
                    opts.outHeight / 2 + height * opts.inSampleSize / 2), opts)
        } catch (e: IOException) {
            null
        }
}