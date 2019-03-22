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

class BitmapLoader(fileCachePath: File) : TaskManager<Callback, Bitmap>() {
    val urlLoader = UrlLoader(fileCachePath, 50 * 1024 * 1024)
    private val memoryCache = object : LruCache<Params, Bitmap>((Math.max(1, Runtime.getRuntime().maxMemory() / MEM_SCALE / 8)).toInt()) {
        override fun sizeOf(key: Params, value: Bitmap) = value.byteCount / MEM_SCALE
    }

    @JvmOverloads
    fun asyncLoad(path: String, @IntRange(from = 0) width: Int = 0, @IntRange(from = 0) height: Int = 0, centerCrop: Boolean = true, tag: Any? = null,
                  onComplete: ((Bitmap) -> Unit)? = null,
                  onFailed: (() -> Unit)? = null,
                  onCanceled: (() -> Unit)? = null): Int {
        val params = Params(path, width, height, centerCrop)
        return asyncStart(params, { Worker(params) }, tag, Callback(onComplete, onFailed, onCanceled)).also { id ->
            if (id < 0) {
                onFailed?.invoke()
            }
        }
    }

    fun syncLoad(path: String, canceled: () -> Boolean, @IntRange(from = 0) width: Int = 0, @IntRange(from = 0) height: Int = 0, centerCrop: Boolean = true): Bitmap? {
        val params = Params(path, width, height, centerCrop)
        return syncStart(params, { Worker(params) }, canceled)
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

    private inner class Worker(private val params: Params) : Task(params) {
        override fun doInBackground(): Bitmap? {
            if (isCanceled)
                return null
            val bitmap = memoryCache[params] ?: kotlin.run {
                val f = File(params.path)
                return@run if (f.exists()) f else urlLoader.syncLoad(params.path, { isCanceled })
            }?.decodeBitmap(params)?.also { memoryCache.put(params, it) }

            postResult = if (bitmap != null) Runnable {
                observers.forEach { it?.onComplete?.invoke(bitmap) }
            } else Runnable {
                observers.forEach { it?.onFailed?.invoke() }
            }
            return bitmap
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
    val (out, dst) = listOf(opts.outHeight to params.height, opts.outWidth to params.width).filter { it.second > 0 }
            .run { return@run if (centerCrop) minBy { it.first.toFloat() / it.second } else maxBy { it.first.toFloat() / it.second } }
            ?: 0 to 0
    opts.inJustDecodeBounds = false

    return if (!centerCrop) BitmapFactory.decodeFile(absolutePath, opts.apply {
        inDensity = out
        inTargetDensity = dst
        inScaled = true
    }) else
        try {
            opts.inSampleSize = out / dst
            val inSampleSize = out.toFloat() / dst
            val bitmap = BitmapRegionDecoder.newInstance(absolutePath, !canWrite()).decodeRegion(Rect(
                    (opts.outWidth / 2 - params.width * inSampleSize / 2).toInt(),
                    (opts.outHeight / 2 - params.height * inSampleSize / 2).toInt(),
                    (opts.outWidth / 2 + params.width * inSampleSize / 2).toInt(),
                    (opts.outHeight / 2 + params.height * inSampleSize / 2).toInt()), opts)
                    ?: return null
            Bitmap.createScaledBitmap(bitmap, params.width, params.height, true)
        } catch (e: IOException) {
            null
        }
}