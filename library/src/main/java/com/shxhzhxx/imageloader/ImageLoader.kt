package com.shxhzhxx.imageloader

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.shxhzhxx.urlloader.TaskManager
import kotlinx.coroutines.*
import java.io.File
import java.lang.Runnable
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round


/**
 * <a href="https://developer.android.com/guide/topics/renderscript/compute">RenderScript</a> developer guide.</p>
 *
 * @param radius       Gaussian blur radius, no more than 25 (limits by android api).
 * @param inSampleSize Since the radius is no more than 25, it may be necessary to discard some of the pixels by sampling
 *                     to achieve a higher degree of blur, especially for high resolution pictures.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
fun blurTransformation(context: Context, bitmap: Bitmap, radius: Float = 16f, inSampleSize: Float = 2f): Bitmap {
    val rs = RenderScript.create(context) //potentially long-running operation
    val width = round(bitmap.width.toDouble() / inSampleSize).toInt()
    val height = round(bitmap.height.toDouble() / inSampleSize).toInt()

    val inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
    val outputBitmap = Bitmap.createBitmap(inputBitmap)

    val intrinsicBlur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    val tmpIn = Allocation.createFromBitmap(rs, inputBitmap)
    val tmpOut = Allocation.createFromBitmap(rs, outputBitmap)

    intrinsicBlur.setRadius(radius)
    intrinsicBlur.setInput(tmpIn)
    intrinsicBlur.forEach(tmpOut)
    tmpOut.copyTo(outputBitmap)
    rs.destroy()
    return outputBitmap
}

class ImageLoader(contentResolver: ContentResolver, fileCachePath: File) : TaskManager<ImageLoader.Holder, Unit>() {
    class Holder(
            val onLoad: (() -> Unit)? = null,
            val onFailure: (() -> Unit)? = null,
            val onCancel: (() -> Unit)? = null
    )

    val bitmapLoader = BitmapLoader(contentResolver, fileCachePath)
    private val lifecycleSet = HashSet<Lifecycle>()
    private val loadRecords = ConcurrentHashMap<Int, String>()

    @JvmOverloads
    fun load(iv: ImageView, path: String?,
             lifecycle: Lifecycle? = (iv.context as? FragmentActivity)?.lifecycle,
             centerCrop: Boolean = true,
             roundingRadius: Int = 0,
             width: Int? = if (iv.isLaidOutCompat) iv.width else null,
             height: Int? = if (iv.isLaidOutCompat) iv.height else null,
             waitForLayout/* will be removed in future. */: Boolean = false,
             placeholder: Int? = if (path == null || loadRecords[iv.hashCode()] != path) 0 else null,// pass 0 will clear current drawable before load
             error: Int? = null,
             transformation: ((Bitmap) -> Bitmap)? = null,
             onLoad: (() -> Unit)? = null,
             onFailure: (() -> Unit)? = null,
             onCancel: (() -> Unit)? = null): Int {
        cancel(iv)
        if (placeholder != null) {
            iv.setImageResource(placeholder)
            loadRecords.remove(iv.hashCode())
        }
        if (path == null) {
            onFailure?.invoke()
            return -1
        }
        if (lifecycle?.currentState == Lifecycle.State.DESTROYED) {
            onCancel?.invoke()
            return -1
        }
        if (lifecycle != null && !lifecycleSet.contains(lifecycle)) {
            lifecycleSet.add(lifecycle)
            lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroy() {
                    lifecycleSet.remove(lifecycle)
                    unregisterByTag(lifecycle)
                }
            })
        }
        return asyncStart(iv, {
            Worker(iv, path, centerCrop, roundingRadius, width, height, waitForLayout, error, transformation)
        }, lifecycle, Holder(onLoad, onFailure, onCancel)).also { id ->
            if (id < 0) {
                onFailure?.invoke()
            }
        }
    }

    private inner class Worker(
            private val iv: ImageView,
            private val path: String,
            private val centerCrop: Boolean,
            private val roundingRadius: Int,
            private val width: Int?,
            private val height: Int?,
            private val waitForLayout: Boolean,
            private val error: Int?,
            private val transformation: ((Bitmap) -> Bitmap)?
    ) : Task(iv) {
        override fun onCancel() {
            observers.forEach { it?.onCancel?.invoke() }
        }

        override fun onObserverUnregistered(observer: Holder?) {
            observer?.onCancel?.invoke()
        }

        override fun doInBackground() {
            val (w, h) = when {
                width != null && height != null -> width to height
                else -> runBlocking(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        iv.waitForLayout()
                    } else {
                        iv.waitForLaidOut(if (waitForLayout) 50 else 10)
                    }
                    (width ?: iv.width) to (height ?: iv.height)
                }
            }
            val bitmap = bitmapLoader.syncLoad(path, { isCancelled }, w, h, centerCrop, roundingRadius, false)?.let {
                transformation?.invoke(it) ?: it
            }
            postResult = if (bitmap != null) Runnable {
                iv.setImageBitmap(bitmap)
                loadRecords[iv.hashCode()] = path
                observers.forEach { it?.onLoad?.invoke() }
            } else Runnable {
                if (error != null) {
                    iv.setImageResource(error)
                    loadRecords.remove(iv.hashCode())
                }
                observers.forEach { it?.onFailure?.invoke() }
            }
        }
    }
}

val View.isLaidOutCompat get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) isLaidOut else (width != 0 || height != 0)

@Deprecated("Arbitrary defined times is unreliable", ReplaceWith("waitForLayout()"))
suspend fun View.waitForLaidOut(times: Int = 10) {
    repeat(times) {
        if (isLaidOutCompat) {
            return@repeat
        }
        delay(10)
    }
}

@RequiresApi(Build.VERSION_CODES.KITKAT)
suspend fun View.waitForLayout() {
    try{
        withTimeout(1000){ while (!isLaidOut) yield() }
    } catch (timeout: TimeoutCancellationException){
        /*
        * To prevent waitForLayout suspend forever,
        * wrap it in withTimeout and omit timeout exception
        *
        * https://issuetracker.google.com/issues/138310612
        * */
    }
}