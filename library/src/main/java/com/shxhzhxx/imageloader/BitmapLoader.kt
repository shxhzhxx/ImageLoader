package com.shxhzhxx.imageloader

import android.content.ContentResolver
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.shxhzhxx.urlloader.TaskManager
import com.shxhzhxx.urlloader.UrlLoader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.math.max
import kotlin.math.min

const val ROUND_CIRCLE = -1
private const val TAG = "BitmapLoader"
private const val MEM_SCALE = 1024


class BitmapLoader(private val contentResolver: ContentResolver, private val fileCachePath: File) : TaskManager<BitmapLoader.Holder, Bitmap>() {
    class Holder(
            val onLoad: ((Bitmap) -> Unit)? = null,
            val onFailure: (() -> Unit)? = null,
            val onCancel: (() -> Unit)? = null
    )

    val urlLoader = UrlLoader(fileCachePath, 50 * 1024 * 1024)
    private val memoryCache = object : LruCache<Params, Bitmap>((max(1, Runtime.getRuntime().maxMemory() / MEM_SCALE / 8)).toInt()) {
        override fun sizeOf(key: Params, value: Bitmap) = value.byteCount / MEM_SCALE
    }

    /**
     * @param path could be url, [File.getAbsolutePath] , [Uri.toString]
     * */
    @JvmOverloads
    fun load(path: String, @IntRange(from = 0) width: Int = 0, @IntRange(from = 0) height: Int = 0, centerCrop: Boolean = true, roundingRadius: Int = 0, precisely: Boolean = false, tag: Any? = null,
             onLoad: ((Bitmap) -> Unit)? = null,
             onFailure: (() -> Unit)? = null,
             onCancel: (() -> Unit)? = null): Int {
        val params = Params(path, width, height, centerCrop, roundingRadius, precisely)
        return asyncStart(params, { Worker(params) }, tag, Holder(onLoad, onFailure, onCancel)).also { id ->
            if (id < 0) {
                onFailure?.invoke()
            }
        }
    }

    fun syncLoad(path: String, canceled: () -> Boolean, @IntRange(from = 0) width: Int = 0, @IntRange(from = 0) height: Int = 0, centerCrop: Boolean = true, roundingRadius: Int = 0, precisely: Boolean = false): Bitmap? {
        val params = Params(path, width, height, centerCrop, roundingRadius, precisely)
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
            if (isCancelled)
                return null
            val bitmap = memoryCache[params] ?: try {
                fun inputStream() = (contentResolver.openInputStream(Uri.parse(params.path))
                        ?: throw FileNotFoundException("Unable to create stream"))
                decodeBitmap({ inputStream() }, params, inputStream().readRotateThenClose())
            } catch (e: FileNotFoundException) {
                val f = File(params.path)
                (if (f.exists()) f else urlLoader.syncLoad(params.path, { isCancelled || (allSyncCanceled && asyncObservers.isEmpty()) }))?.decodeBitmap(params)
            }?.also {
                if (it.byteCount / MEM_SCALE < memoryCache.size() / 2) {
                    memoryCache.put(params, it)
                }
            }

            postResult = if (bitmap != null) Runnable {
                observers.forEach { it?.onLoad?.invoke(bitmap) }
            } else Runnable {
                observers.forEach { it?.onFailure?.invoke() }
            }
            return bitmap
        }

        override fun onObserverUnregistered(observer: Holder?) {
            observer?.onCancel?.invoke()
        }

        override fun onCancel() {
            observers.forEach { it?.onCancel?.invoke() }
        }
    }


    private fun InputStream.readRotateThenClose() = use {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return@use try {
                ExifInterface(this).readRotate()
            } catch (e: IOException) {
                0
            }
        } else {
            val file = File(fileCachePath, urlLoader.md5(UUID.randomUUID().toString()))
            return@use try {
                file.outputStream().use { copyTo(it) }
                file.readRotate()
            } catch (e: IOException) {
                0
            } finally {
                file.delete()
            }
        }
    }

    private fun decodeBitmap(getInputStream: () -> InputStream, params: Params, rotate: Int): Bitmap? {
        fun decodeStream(inputStream: InputStream, opts: BitmapFactory.Options) =
                inputStream.use {
                    try {
                        BitmapFactory.decodeStream(it, null, opts)
                    } catch (e: Throwable) {
                        null
                    }
                }

        val centerCrop = params.centerCrop && params.height > 0 && params.width > 0
        if (params.height <= 0 && params.width <= 0) {
            Log.e(TAG, "load bitmap without compress")
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeStream(getInputStream(), opts)
        val (dstWidth, dstHeight) = when (rotate) {
            90, 270 -> params.height to params.width
            else -> params.width to params.height
        }
        val (out, dst) = listOf(opts.outHeight to dstHeight, opts.outWidth to dstWidth).filter { it.second > 0 }
                .run { return@run if (centerCrop) minBy { it.first.toFloat() / it.second } else maxBy { it.first.toFloat() / it.second } }
                ?: 0 to 0
        opts.inJustDecodeBounds = false
        opts.inSampleSize = if (dst == 0) 0 else out / dst

        return (if (!centerCrop) {
            decodeStream(getInputStream(), opts)?.let { bitmap ->
                val resizeFactor = listOf(dstWidth to bitmap.width, dstHeight to bitmap.height).filter { it.first > 0 && it.second > 0 }
                        .map { it.first.toFloat() / it.second }.min() ?: return@let bitmap
                return@let if (!params.precisely || resizeFactor == 1f) bitmap else {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * resizeFactor).toInt(), (bitmap.height * resizeFactor).toInt(), true)
                }
            }
        } else {
            getInputStream().use { inputStream ->
                try {
                    val inSampleSize = out.toFloat() / dst
                    val bitmap = BitmapRegionDecoder.newInstance(inputStream, false).decodeRegion(Rect(
                            ((opts.outWidth / 2 - dstWidth * inSampleSize / 2).toInt()),
                            ((opts.outHeight / 2 - dstHeight * inSampleSize / 2).toInt()),
                            ((opts.outWidth / 2 + dstWidth * inSampleSize / 2).toInt()),
                            ((opts.outHeight / 2 + dstHeight * inSampleSize / 2).toInt())), opts)
                            ?: return null

                    val resizeFactor = listOf(dstWidth to bitmap.width, dstHeight to bitmap.height).find { it.first > 0 && it.second > 0 }
                            ?.let { it.first.toFloat() / it.second } ?: return@use bitmap
                    if (!params.precisely || resizeFactor == 1f) bitmap else Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, true)
                } catch (e: IOException) {
                    null
                }
            }
        })?.let { bitmap ->
            if (rotate == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotate.toFloat()) }, true)
        }?.let { bitmap ->
            when {
                params.roundingRadius == 0 -> bitmap
                params.roundingRadius < 0 -> bitmap.toRounded(min(bitmap.width, bitmap.height).toFloat() / 2)
                else -> bitmap.toRounded(params.roundingRadius.toFloat())
            }
        }
    }

    private fun Bitmap.toRounded(radius: Float) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).also { canvas ->
            canvas.drawRoundRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), radius, radius, Paint().also { paint ->
                paint.isAntiAlias = true
                paint.shader = BitmapShader(this, Shader.TileMode.CLAMP,
                        Shader.TileMode.CLAMP)
            })
            canvas.setBitmap(null)
        }
    }

    private fun ExifInterface.readRotate() = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun File.readRotate() = try {
        ExifInterface(absolutePath).readRotate()
    } catch (e: IOException) {
        0
    }


    private fun File.decodeBitmap(params: Params) = try {
        decodeBitmap({ inputStream() }, params, readRotate())
    } catch (e: FileNotFoundException) {
        null
    }
}

data class Params(val path: String, val width: Int, val height: Int, val centerCrop: Boolean, val roundingRadius: Int, val precisely: Boolean)

