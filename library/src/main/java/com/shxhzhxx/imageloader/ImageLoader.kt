package com.shxhzhxx.imageloader

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.shxhzhxx.urlloader.TaskManager
import java.io.File


class ICallback(
        val onComplete: (() -> Unit)? = null,
        val onFailed: (() -> Unit)? = null,
        val onCanceled: (() -> Unit)? = null
)


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
    val width = Math.round(bitmap.width.toDouble() / inSampleSize).toInt()
    val height = Math.round(bitmap.height.toDouble() / inSampleSize).toInt()

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

class ImageLoader(fileCachePath: File) : TaskManager<ICallback, Unit>() {
    val bitmapLoader = BitmapLoader(fileCachePath)
    private val lifecycleSet = HashSet<Lifecycle>()

    fun load(iv: ImageView, path: String?, lifecycle: Lifecycle? = null,
             centerCrop: Boolean = true,
             width: Int? = null,
             height: Int? = null,
             waitForLayout: Boolean = false,
             placeholder: Int? = null,
             error: Int? = null,
             transformation: ((Bitmap) -> Bitmap)? = null,
             onComplete: (() -> Unit)? = null,
             onFailed: (() -> Unit)? = null,
             onCanceled: (() -> Unit)? = null): Int {
        cancel(iv)
        if (placeholder != null)
            iv.setImageResource(placeholder)
        if (path == null) {
            onFailed?.invoke()
            return -1
        }
        val lc = lifecycle ?: iv.context.let { if (it is FragmentActivity) it.lifecycle else null }
        if (lc != null && !lifecycleSet.contains(lc)) {
            lifecycleSet.add(lc)
            lc.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroy() {
                    lifecycleSet.remove(lc)
                    unregisterByTag(lc)
                }
            })
        }
        return asyncStart(iv, { Worker(iv, path, centerCrop, width, height, waitForLayout, error, transformation) }
                , lc, ICallback(onComplete, onFailed, onCanceled)).also { id ->
            if (id < 0) {
                onFailed?.invoke()
            }
        }
    }

    private inner class Worker(
            private val iv: ImageView,
            private val path: String,
            private val centerCrop: Boolean,
            private val width: Int?,
            private val height: Int?,
            private val waitForLayout: Boolean,
            private val error: Int?,
            private val transformation: ((Bitmap) -> Bitmap)?
    ) : Task(iv) {
        override fun onCanceled() {
            observers.forEach { it?.onCanceled?.invoke() }
        }

        override fun onObserverUnregistered(observer: ICallback?) {
            observer?.onCanceled?.invoke()
        }

        override fun doInBackground() {
            val (w, h) = when {
                width != null || height != null -> (width ?: 0) to (height ?: 0)
                else -> {
                    var cnt = if (waitForLayout) 50 else 5
                    while (!isViewLaidOut(iv) && cnt-- > 0) {
                        Thread.sleep(10)
                    }
                    iv.width to iv.height
                }
            }
            val bitmap = bitmapLoader.syncLoad(path, { isCanceled }, w, h, centerCrop)?.let {
                return@let transformation?.invoke(it) ?: it
            }
            postResult = if (bitmap != null) Runnable {
                iv.setImageBitmap(bitmap)
                observers.forEach { it?.onComplete?.invoke() }
            } else Runnable {
                if (error != null)
                    iv.setImageResource(error)
                observers.forEach { it?.onFailed?.invoke() }
            }
        }
    }
}

private fun isViewLaidOut(view: View) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) view.isLaidOut else (view.width != 0 || view.height != 0)