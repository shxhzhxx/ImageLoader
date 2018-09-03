package com.shxhzhxx.imageloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.widget.ImageView;

import com.shxhzhxx.urlloader.MultiObserverTaskManager;
import com.shxhzhxx.urlloader.UrlLoader;

import java.io.File;

public class ImageLoader extends MultiObserverTaskManager<ImageLoader.Callback> {
    private static final String TAG = "ImageLoader";

    public abstract static class Callback {

        public void onComplete() {
        }

        public void onFailed() {
        }

        public void onCanceled() {
        }
    }


    public static final int CONFIG_CENTER_CROP = BitmapLoader.CONFIG_CENTER_CROP;

    private static final int CONFIG_MASK = 0x0FFF;

    /**
     * By default,ImageLoader will wait view measure itself for 320 milliseconds at most.
     * With this configuration, ImageLoader will wait 3200 milliseconds.
     */
    public static final int CONFIG_WAIT_VIEW_MEASURE = 1 << 16;
    public static final int CONFIG_LOAD_WITHOUT_CLEAR = 1 << 17;

    private static volatile ImageLoader mInstance;

    public static synchronized void init(@NonNull Context context) {
        if (mInstance == null)
            mInstance = new ImageLoader(context.getCacheDir());
    }

    public static ImageLoader getInstance() {
        return mInstance;
    }

    public class Builder {
        private Callback mCallback = null;
        private int mWidth = 0, mHeight = 0, mConfig = 0;
        private String mPath = null, mTag = null;
        private File mFile = null;

        Builder(String path) {
            mPath = path;
        }

        Builder(File file) {
            mFile = file;
        }

        public Builder tag(String tag) {
            mTag = tag;
            return this;
        }

        public Builder resize(int width, int height) {
            mWidth = width;
            mHeight = height;
            return this;
        }

        public Builder config(int config) {
            mConfig = config;
            return this;
        }

        public Builder centerCrop() {
            mConfig = mConfig | CONFIG_CENTER_CROP;
            return this;
        }

        public Builder waitMeasure() {
            mConfig = mConfig | CONFIG_WAIT_VIEW_MEASURE;
            return this;
        }

        public Builder withoutClear() {
            mConfig = mConfig | CONFIG_LOAD_WITHOUT_CLEAR;
            return this;
        }

        public Builder callback(Callback callback) {
            mCallback = callback;
            return this;
        }

        boolean checkParams() {
            return !TextUtils.isEmpty(mPath) || (mFile != null && mFile.exists());
        }

        public int into(ImageView view) {
            return execute(view, this);
        }
    }

    private BitmapLoader mBitmapLoader;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public ImageLoader(File diskCachePath) {
        super(5);
        mBitmapLoader = new BitmapLoader(diskCachePath);
    }

    public BitmapLoader getBitmapLoader() {
        return mBitmapLoader;
    }

    public UrlLoader getUrlLoader() {
        return mBitmapLoader.getUrlLoader();
    }

    public Builder load(String path) {
        return new Builder(path);
    }

    public Builder load(File file) {
        return new Builder(file);
    }

    @MainThread
    private int execute(final ImageView view, final Builder builder) {
        final String key = String.valueOf(view.hashCode());
        if (isRunning(key))
            cancel(key);
        return start(key, builder.mTag, builder.mCallback, new TaskBuilder() {
            @Override
            public Task build() {
                return new WorkThread(key, view, builder);
            }
        });
    }

    private class WorkThread extends Task {
        private final ImageView mView;
        private int mCountdown = 20;
        private volatile int mId = -1;
        private volatile boolean mSuccess = false, mLoaded = false;
        private final String mPath;
        private File mFile;
        private int mConfig, mWidth, mHeight;

        WorkThread(String key, ImageView view, Builder builder) {
            super(key);
            mView = view;
            mPath = builder.mPath;
            mFile = builder.mFile;
            mConfig = builder.mConfig;
            mWidth = builder.mWidth;
            mHeight = builder.mHeight;

            if ((mConfig & CONFIG_LOAD_WITHOUT_CLEAR) == 0) {
                mView.setImageDrawable(null);
            }
            if (mWidth == 0 && mHeight == 0) {
                mWidth = mView.getWidth();
                mHeight = mView.getHeight();
            }
        }

        @Override
        protected void onCanceled() {
            for (Callback observer : getObservers()) {
                if (observer != null)
                    observer.onCanceled();
            }
            if (mId >= 0)
                mBitmapLoader.cancel(mId);
        }

        @Override
        protected void onObserverUnregistered(Callback observer) {
            if (observer != null)
                observer.onCanceled();
        }

        @Override
        protected void doInBackground() {
            if ((mConfig & CONFIG_WAIT_VIEW_MEASURE) != 0) {
                mCountdown *= 10;
            }
            while (!isCanceled() && mWidth == 0 && mHeight == 0 && mCountdown-- >= 0) {
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    return;
                }
                mWidth = mView.getWidth();
                mHeight = mView.getHeight();
            }
            if (isCanceled())
                return;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    BitmapLoader.ProgressObserver observer = new BitmapLoader.ProgressObserver() {
                        @Override
                        public void onComplete(Bitmap bitmap) {
                            mView.setImageBitmap(bitmap);
                            mSuccess = true;
                            onLoaded();
                        }

                        @Override
                        public void onCanceled() {
                            onLoaded();
                        }

                        @Override
                        public void onFailed() {
                            onLoaded();
                        }

                        private void onLoaded() {
                            synchronized (WorkThread.this) {
                                mLoaded = true;
                                mId = -1;
                                WorkThread.this.notify();
                            }
                        }
                    };
                    if (mFile != null)
                        mId = mBitmapLoader.load(mFile, mWidth, mHeight, mConfig, observer);
                    else
                        mId = mBitmapLoader.load(mPath, mWidth, mHeight, mConfig, observer);
                }
            });
            synchronized (WorkThread.this) {
                while (!isCanceled() && !mLoaded) {
                    try {
                        WorkThread.this.wait();
                    } catch (InterruptedException ignore) {
                    }
                }
            }
            if (isCanceled())
                return;
            if (mSuccess) {
                setPostResult(new Runnable() {
                    @Override
                    public void run() {
                        for (Callback callback : getObservers()) {
                            if (callback != null)
                                callback.onComplete();
                        }
                    }
                });
            } else {
                setPostResult(new Runnable() {
                    @Override
                    public void run() {
                        for (Callback callback : getObservers()) {
                            if (callback != null)
                                callback.onFailed();
                        }
                    }
                });
            }
        }
    }
}
