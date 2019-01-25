package com.shxhzhxx.imageloader;

public class ImageLoader /*extends TaskManager<ImageLoader.Callback>*/ {
//    private static final String TAG = "ImageLoader";
//
//    public interface Transformation {
//        Bitmap transform(Context context, Bitmap bitmap);
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
//    public static class BlurTransformation implements Transformation {
//        private float radius, inSampleSize;
//
//        public BlurTransformation() {
//            this(16, 2);
//        }
//
//        /**
//         * @param radius       Gaussian blur radius, no more than 25 (limits by android api).
//         * @param inSampleSize Since the radius is no more than 25, it may be necessary to discard some of the pixels by sampling
//         *                     to achieve a higher degree of blur, especially for high resolution pictures.
//         */
//        public BlurTransformation(@FloatRange(from = 1, to = 25) float radius, @FloatRange(from = 1) float inSampleSize) {
//            this.radius = radius;
//            this.inSampleSize = inSampleSize;
//        }
//
//        /**
//         * <a href="https://developer.android.com/guide/topics/renderscript/compute">RenderScript</a> developer guide.</p>
//         */
//        @Override
//        public Bitmap transform(Context context, Bitmap bitmap) {
//            RenderScript rs = RenderScript.create(context); //potentially long-running operation
//            int width = Math.round(bitmap.getWidth() / inSampleSize);
//            int height = Math.round(bitmap.getHeight() / inSampleSize);
//
//            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
//            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);
//
//            ScriptIntrinsicBlur intrinsicBlur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
//            Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
//            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
//
//            intrinsicBlur.setRadius(radius);
//            intrinsicBlur.setInput(tmpIn);
//            intrinsicBlur.forEach(tmpOut);
//            tmpOut.copyTo(outputBitmap);
//            rs.destroy();
//            return outputBitmap;
//        }
//    }
//
//    public abstract static class Callback {
//
//        public void onComplete() {
//        }
//
//        public void onFailed() {
//        }
//
//        public void onCanceled() {
//        }
//    }
//
//
//    public static final int CONFIG_CENTER_CROP = BitmapLoader.CONFIG_CENTER_CROP;
//
//    private static final int CONFIG_MASK = 0x0FFF;
//
//    /**
//     * By default,ImageLoader will wait view measure itself for 320 milliseconds at most.
//     * With this configuration, ImageLoader will wait 3200 milliseconds.
//     */
//    public static final int CONFIG_WAIT_VIEW_MEASURE = 1 << 16;
//
//    /**
//     * By default,ImageLoader will clear current picture of the view at start.
//     * Set this configuration to forbid this.
//     */
//    public static final int CONFIG_LOAD_WITHOUT_CLEAR = 1 << 17;
//
//    /**
//     * By default,ImageLoader will not do anything about view when failed or canceled.
//     * With this configuration, ImageLoader will call view's {@link ImageView#setImageDrawable(Drawable)} with null parameter
//     */
//    public static final int CONFIG_CLEAR_WHEN_FAILED = 1 << 18;
//
//    private static volatile ImageLoader mInstance;
//
//    public static synchronized void init(@NonNull Context context) {
//        if (mInstance == null)
//            mInstance = new ImageLoader(context.getCacheDir());
//    }
//
//    public static ImageLoader getInstance() {
//        return mInstance;
//    }
//
//    public class Builder {
//        private Callback mCallback = null;
//        private int mWidth = 0, mHeight = 0, mConfig = 0;
//        private String mPath = null, mTag = null;
//        private File mFile = null;
//        private Transformation mTransformation = null;
//
//        Builder(String path) {
//            mPath = path;
//        }
//
//        Builder(File file) {
//            mFile = file;
//        }
//
//        public Builder tag(String tag) {
//            mTag = tag;
//            return this;
//        }
//
//        public Builder resize(int width, int height) {
//            mWidth = width;
//            mHeight = height;
//            return this;
//        }
//
//        public Builder config(int config) {
//            mConfig = config;
//            return this;
//        }
//
//        public Builder centerCrop() {
//            mConfig = mConfig | CONFIG_CENTER_CROP;
//            return this;
//        }
//
//        public Builder waitMeasure() {
//            mConfig = mConfig | CONFIG_WAIT_VIEW_MEASURE;
//            return this;
//        }
//
//        public Builder withoutClear() {
//            mConfig = mConfig | CONFIG_LOAD_WITHOUT_CLEAR;
//            return this;
//        }
//
//        public Builder clearWhenFailed() {
//            mConfig = mConfig | CONFIG_CLEAR_WHEN_FAILED;
//            return this;
//        }
//
//        public Builder callback(Callback callback) {
//            mCallback = callback;
//            return this;
//        }
//
//        public Builder transformation(Transformation transformation) {
//            mTransformation = transformation;
//            return this;
//        }
//
//        public int into(ImageView view) {
//            return execute(view, this);
//        }
//    }
//
//    private BitmapLoader mBitmapLoader;
//    private Handler mHandler = new Handler(Looper.getMainLooper());
//
//    public ImageLoader(File diskCachePath) {
//        super(5);
//        mBitmapLoader = new BitmapLoader(diskCachePath);
//    }
//
//    public BitmapLoader getBitmapLoader() {
//        return mBitmapLoader;
//    }
//
//    public UrlLoader getUrlLoader() {
//        return mBitmapLoader.getUrlLoader();
//    }
//
//    public Builder load(String path) {
//        return new Builder(path);
//    }
//
//    public Builder load(File file) {
//        return new Builder(file);
//    }
//
//    @MainThread
//    private int execute(final ImageView view, final Builder builder) {
//        final String key = String.valueOf(view.hashCode());
//        cancel(key);
//        int id = start(key, builder.mTag, builder.mCallback, new TaskBuilder() {
//            @Override
//            public Task build() {
//                return new WorkThread(key, view, builder);
//            }
//        });
//        if (id < 0 && builder.mCallback != null) {
//            builder.mCallback.onFailed();
//        }
//        return id;
//    }
//
//    private class WorkThread extends Task {
//        private final ImageView mView;
//        private final Transformation mTransformation;
//        private int mCountdown = 20;
//        private volatile int mId = -1;
//        private volatile boolean mLoaded = false;
//        private volatile Bitmap mBitmap = null;
//        private final String mPath;
//        private File mFile;
//        private int mConfig, mWidth, mHeight;
//
//        WorkThread(String key, ImageView view, Builder builder) {
//            super(key);
//            mView = view;
//            mTransformation = builder.mTransformation;
//            mPath = builder.mPath;
//            mFile = builder.mFile;
//            mConfig = builder.mConfig;
//            mWidth = builder.mWidth;
//            mHeight = builder.mHeight;
//
//            if ((mConfig & CONFIG_LOAD_WITHOUT_CLEAR) == 0) {
//                mView.setImageDrawable(null);
//            }
//            if (mWidth == 0 && mHeight == 0) {
//                mWidth = mView.getWidth();
//                mHeight = mView.getHeight();
//            }
//        }
//
//        @Override
//        protected void onCanceled() {
//            if ((mConfig & CONFIG_CLEAR_WHEN_FAILED) != 0) {
//                mView.setImageDrawable(null);
//            }
//            if (mId >= 0)
//                mBitmapLoader.cancel(mId);
//            for (Callback observer : getObservers()) {
//                if (observer != null)
//                    observer.onCanceled();
//            }
//        }
//
//        @Override
//        protected void onObserverUnregistered(Callback observer) {
//            if (observer != null)
//                observer.onCanceled();
//        }
//
//        @Override
//        protected void doInBackground() {
//            if ((mConfig & CONFIG_WAIT_VIEW_MEASURE) != 0) {
//                mCountdown *= 10;
//            }
//            while (!isCanceled() && mWidth == 0 && mHeight == 0 && mCountdown-- >= 0) {
//                try {
//                    Thread.sleep(16);
//                } catch (InterruptedException e) {
//                    return;
//                }
//                mWidth = mView.getWidth();
//                mHeight = mView.getHeight();
//            }
//            if (isCanceled())
//                return;
//            mHandler.post(new Runnable() {
//                @Override
//                public void run() {
//                    if (isCanceled())
//                        return;
//                    BitmapLoader.ProgressObserver observer = new BitmapLoader.ProgressObserver() {
//                        @Override
//                        public void onComplete(Bitmap bitmap) {
//                            mBitmap = bitmap;
//                            onLoaded();
//                        }
//
//                        @Override
//                        public void onCanceled() {
//                            onLoaded();
//                        }
//
//                        @Override
//                        public void onFailed() {
//                            onLoaded();
//                        }
//
//                        private void onLoaded() {
//                            synchronized (WorkThread.this) {
//                                mLoaded = true;
//                                mId = -1;
//                                WorkThread.this.notify();
//                            }
//                        }
//                    };
//                    if (mFile != null)
//                        mId = mBitmapLoader.load(mFile, mWidth, mHeight, mConfig, observer);
//                    else
//                        mId = mBitmapLoader.load(mPath, mWidth, mHeight, mConfig, observer);
//                }
//            });
//            synchronized (WorkThread.this) {
//                while (!isCanceled() && !mLoaded) {
//                    try {
//                        WorkThread.this.wait();
//                    } catch (InterruptedException ignore) {
//                    }
//                }
//            }
//            if (isCanceled())
//                return;
//            if (mBitmap != null && mTransformation != null) {
//                //noinspection NonAtomicOperationOnVolatileField
//                mBitmap = mTransformation.transform(mView.getContext(), mBitmap);
//            }
//            if (mBitmap != null) {
//                setPostResult(new Runnable() {
//                    @Override
//                    public void run() {
//                        mView.setImageBitmap(mBitmap);
//                        for (Callback callback : getObservers()) {
//                            if (callback != null)
//                                callback.onComplete();
//                        }
//                    }
//                });
//            } else {
//                setPostResult(new Runnable() {
//                    @Override
//                    public void run() {
//                        if ((mConfig & CONFIG_CLEAR_WHEN_FAILED) != 0) {
//                            mView.setImageDrawable(null);
//                        }
//                        for (Callback callback : getObservers()) {
//                            if (callback != null)
//                                callback.onFailed();
//                        }
//                    }
//                });
//            }
//        }
//    }
}
