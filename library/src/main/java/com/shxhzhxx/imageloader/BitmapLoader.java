package com.shxhzhxx.imageloader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import com.shxhzhxx.urlloader.MultiObserverTaskManager;
import com.shxhzhxx.urlloader.UrlLoader;

import java.io.File;
import java.io.IOException;

public class BitmapLoader extends MultiObserverTaskManager<BitmapLoader.ProgressObserver> {
    private static final String TAG = "BitmapLoader";
    private static final int MEM_SCALE = 1024; //mem unit: kilo byte

    private static final int CONFIG_MASK = 0x00FF;

    /**
     * By default,bitmap will be resized so that its relatively longer edges can match the desired size.
     * With this configuration, the bitmap will be resized so that its relatively shorter edges can match the desired size and longer edges will be cropped.
     * Currently only the JPEG and PNG formats are supported
     */
    public static final int CONFIG_CENTER_CROP = 1;


    public abstract static class ProgressObserver {
        /**
         * @param bitmap immutable bitmap
         */
        public void onComplete(Bitmap bitmap) {
        }

        public void onFailed() {
        }

        public void onCanceled() {
        }
    }


    @Nullable
    public static Bitmap loadBitmap(File file, int width, int height) {
        if (height <= 0 && width <= 0) {
            Log.e(TAG, "load bitmap without compress :" + file.getAbsolutePath());
        }
        if (height <= 0) {
            height = Integer.MAX_VALUE;
        }
        if (width <= 0) {
            width = Integer.MAX_VALUE;
        }
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opt);

        double inSampleSize = Math.max((double) opt.outWidth / (double) width, (double) opt.outHeight / (double) height);
        opt = new BitmapFactory.Options();
        opt.inSampleSize = (int) inSampleSize;
        opt.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opt); //初步压缩分辨率
        if (bitmap == null)
            return null;
        if (inSampleSize <= 1) {
            return bitmap;
        }
        inSampleSize = Math.max((double) bitmap.getWidth() / (double) width, (double) bitmap.getHeight() / (double) height);
        return Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() / inSampleSize), (int) (bitmap.getHeight() / inSampleSize), true); //进一步压缩分辨率
    }

    @Nullable
    public static Bitmap loadBitmapCrop(File file, int width, int height) {
        if (width <= 0 || height <= 0) {
            Log.e(TAG, "loadBitmapCrop: crop need explicit width and height");
            return loadBitmap(file, width, height);
        }
        BitmapRegionDecoder decoder;
        try {
            decoder = BitmapRegionDecoder.newInstance(file.getAbsolutePath(), false);
        } catch (IOException e) {
            Log.e(TAG, "loadBitmapCrop IOException:" + e.getMessage());
            return null;
        }
        int rawW = decoder.getWidth();
        int rawH = decoder.getHeight();
        double inSampleSize = Math.min((double) rawW / (double) width, (double) rawH / (double) height);
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = (int) inSampleSize;
        int left = (int) (rawW / 2 - width * inSampleSize / 2);
        int right = (int) (rawW / 2 + width * inSampleSize / 2);
        int top = (int) (rawH / 2 - height * inSampleSize / 2);
        int bottom = (int) (rawH / 2 + height * inSampleSize / 2);
        Bitmap bitmap = decoder.decodeRegion(new Rect(left, top, right, bottom), opt);
        if (bitmap == null)
            return null;
        if (inSampleSize <= 1) {
            return bitmap;
        }
        inSampleSize = Math.min((double) bitmap.getWidth() / (double) width, (double) bitmap.getHeight() / (double) height);
        return Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() / inSampleSize), (int) (bitmap.getHeight() / inSampleSize), true);
    }

    private UrlLoader mUrlLoader;
    private LruCache<String, Bitmap> mMemoryCache;

    public BitmapLoader(@NonNull File cachePath) {
        super(5);
        mUrlLoader = new UrlLoader(cachePath, 50 * 1024 * 1024, 5);
        mMemoryCache = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / (MEM_SCALE * 8))) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / MEM_SCALE;
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                /* The old bitmap may now be used by other views, so the oldValue can not be reclaimed here.
                 *  actually, calling bitmap.recycle() is needless on Android 3.0 and higher
                 */
            }
        };
    }

    @MainThread
    private String getKey(String path, int width, int height, int config) {
        return mUrlLoader.md5(mUrlLoader.md5(mUrlLoader.md5(mUrlLoader.md5(path) + width) + height) + (config & CONFIG_MASK));
    }

    public UrlLoader getUrlLoader() {
        return mUrlLoader;
    }

    public int load(final String path, @Nullable String tag, final int width, final int height, final int config, ProgressObserver observer) {
        if (TextUtils.isEmpty(path) || width < 0 || height < 0) {
            Log.e(TAG, TAG + ".load: invalid params");
            if (observer != null)
                observer.onFailed();
            return -1;
        }
        File file = new File(path);
        if (file.exists()) {
            return load(file, tag, width, height, config, observer);
        }
        String key = getKey(path, width, height, config);
        Bitmap bitmap = mMemoryCache.get(key);
        if (bitmap != null) {
            if (observer != null)
                observer.onComplete(bitmap);
            return -2;
        }
        int id = start(key, tag, observer, new TaskBuilder() {
            @Override
            public Task build() {
                return new WorkThread(path, width, height, config);
            }
        });
        if (id < 0 && observer != null) {
            observer.onFailed();
        }
        return id;
    }

    public int load(String path, int width, int height, int config, ProgressObserver observer) {
        return load(path, null, width, height, config, observer);
    }

    public int load(final File file, @Nullable String tag, final int width, final int height, final int config, ProgressObserver observer) {
        if (file == null || !file.exists() || width < 0 || height < 0) {
            Log.e(TAG, TAG + ".load: invalid params");
            if (observer != null)
                observer.onFailed();
            return -1;
        }
        String key = getKey(file.getAbsolutePath(), width, height, config);
        Bitmap bitmap = mMemoryCache.get(key);
        if (bitmap != null) {
            if (observer != null)
                observer.onComplete(bitmap);
            return -2;
        }
        int id = start(key, tag, observer, new TaskBuilder() {
            @Override
            public Task build() {
                return new WorkThread(file, width, height, config);
            }
        });
        if (id < 0 && observer != null) {
            observer.onFailed();
        }
        return id;
    }

    public int load(final File file, final int width, final int height, final int config, ProgressObserver observer) {
        return load(file, null, width, height, config, observer);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void clearMemCache() {
        mMemoryCache.trimToSize(-1);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void trimMemory(boolean resizeCache) {
        Log.d(TAG, "trimMemory  resizeCache:" + resizeCache);
        int size = mMemoryCache.size();
        if (size < 2) // can't be smaller than 1
            return;
        if (resizeCache && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMemoryCache.resize(size / 2);
        } else {
            mMemoryCache.trimToSize(size / 2);
        }
    }

    private class WorkThread extends Task {
        private volatile File mFile;
        private Bitmap mBitmap;
        private int mHeight, mWidth, mConfig;
        private volatile int mDownloadId;

        @MainThread
        WorkThread(String url, int width, int height, int config) {
            super(BitmapLoader.this.getKey(url, width, height, config));
            mWidth = width;
            mHeight = height;
            mConfig = config;

            //work thread doesn't start yet ,no need atomic operation
            //noinspection NonAtomicOperationOnVolatileField
            mDownloadId = mUrlLoader.load(url, new UrlLoader.ProgressObserver() {
                @Override
                public void onComplete(File file) {
                    synchronized (WorkThread.this) {
                        mFile = file;
                        mDownloadId = -1;
                        WorkThread.this.notify();
                    }
                }

                @Override
                public void onCanceled() {
                    synchronized (WorkThread.this) {
                        if (mDownloadId >= 0) {
                            WorkThread.this.notify();
                            mDownloadId = -1;
                        }
                    }
                }

                @Override
                public void onFailed() {
                    synchronized (WorkThread.this) {
                        mDownloadId = -1;
                        WorkThread.this.notify();
                    }
                }
            });
        }

        WorkThread(File file, int width, int height, int config) {
            super(BitmapLoader.this.getKey(file.getAbsolutePath(), width, height, config));
            mFile = file;
            mWidth = width;
            mHeight = height;
            mConfig = config;
            mDownloadId = -1;
        }

        /**
         * check {@link #mDownloadId} in main thread, don't need to synchronize.
         * {@link #wait()} will be aroused by interrupt()
         */
        @Override
        @MainThread
        protected void onCanceled() {
            if (mDownloadId >= 0 && mUrlLoader.cancel(mDownloadId)) {
                mDownloadId = -1;
            }
            for (ProgressObserver observer : getObservers()) {
                if (observer != null)
                    observer.onCanceled();
            }
        }

        @Override
        protected void onObserverUnregistered(ProgressObserver observer) {
            if (observer != null)
                observer.onCanceled();
        }

        @Nullable
        private Bitmap load() {
            try {
                if ((mConfig & CONFIG_CENTER_CROP) != 0) {
                    return loadBitmapCrop(mFile, mWidth, mHeight);
                } else {
                    return loadBitmap(mFile, mWidth, mHeight);
                }
            } catch (Exception e) {
                Log.e(TAG, "load Exception: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void doInBackground() {
            synchronized (WorkThread.this) {
                while (!isCanceled() && mDownloadId >= 0) {
                    try {
                        WorkThread.this.wait();
                    } catch (InterruptedException ignore) {
                    }
                }
            }
            if (isCanceled())
                return;
            if (mFile != null && mFile.exists() && (mBitmap = load()) != null) {
                if ((mBitmap.getByteCount() / MEM_SCALE) < (mMemoryCache.maxSize() / 2))
                    mMemoryCache.put(getKey(), mBitmap);
                setPostResult(new Runnable() {
                    @Override
                    public void run() {
                        for (ProgressObserver observer : getObservers()) {
                            if (observer != null)
                                observer.onComplete(mBitmap);
                        }
                    }
                });
            } else {
                setPostResult(new Runnable() {
                    @Override
                    public void run() {
                        for (ProgressObserver observer : getObservers()) {
                            if (observer != null)
                                observer.onFailed();
                        }
                    }
                });
            }
        }
    }
}
