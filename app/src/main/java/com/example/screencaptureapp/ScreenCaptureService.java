package com.example.screencaptureapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int FRAME_QUEUE_CAPACITY = 1;
    private static final int CAPTURE_WAIT_MAX_ROUNDS = 10;
    private static final int CAPTURE_WAIT_SLICE_MS = 50;
    private static final int CAPTURE_RECOVERY_SLEEP_MS = 100;
    private static final int EMPTY_ROUNDS_BEFORE_REBUILD = 6;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread handlerThread;
    private Handler handler;
    private int screenDensity;
    private int screenWidth;
    private int screenHeight;
    private volatile boolean isRecording;
    private LinkedBlockingQueue<CaptureFrame> frameQueue;
    private Thread uploadThread;
    private final AtomicBoolean isStopping = new AtomicBoolean(false);
    private final IBinder binder = new LocalBinder();
    private NetworkManager networkManager;
    private Thread captureControlThread;
    private final Object captureLock = new Object();
    private final Object stopLock = new Object();
    private volatile boolean requestCleanFrame;
    private CaptureFrame latestCleanFrame;
    private byte[] pixelDataBuffer;
    private int cachedPixelStride;
    private int cachedRowStride;
    private long latestFrameTimestamp;
    private volatile boolean hasNewFrame;
    private ByteBuffer reusablePixelBuffer;
    private Bitmap reusablePaddedBitmap;
    private Rect reusableCropRect;
    private int reusablePaddedWidth;
    private int reusablePixelStride;
    private int reusableRowStride;
    private int emptyRoundCount;
    private int imgCallbackCount;
    private long lastAcceptedFrameTimeMs;
    private volatile long latestEnqueuedFrameTimeMs;
    private volatile long latestDisplayedFrameTimeMs;
    private final AtomicBoolean capturePipelineRebuildInFlight = new AtomicBoolean(false);
    private final AtomicInteger capturePipelineGeneration = new AtomicInteger(0);
    private static final StringBuilder logBuf = new StringBuilder();

    public static void blog(String msg) {
        String line = System.currentTimeMillis() + "|" + msg;
        Log.d("GTO", line);
        synchronized (logBuf) {
            logBuf.append(line).append("\n");
            if (logBuf.length() > 20000) {
                logBuf.delete(0, logBuf.length() - 15000);
            }
        }
    }

    public static class CaptureFrame {
        public final Bitmap bitmap;
        public final long captureTimeMs;

        public CaptureFrame(Bitmap bitmap, long captureTimeMs) {
            this.bitmap = bitmap;
            this.captureTimeMs = captureTimeMs;
        }
    }

    public class LocalBinder extends Binder {
        ScreenCaptureService getService() {
            return ScreenCaptureService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        updateScreenDimensions();
        blog("SVC: onCreate, scr=" + screenWidth + "x" + screenHeight + " dpi=" + screenDensity);
        handlerThread = new HandlerThread("ScreenCapture");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        blog("SVC: handlerThread started");
    }

    private void updateScreenDimensions() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            throw new IllegalStateException("Critical Error: WindowManager is null. Cannot retrieve screen dimensions. Terminating application.");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = windowManager.getMaximumWindowMetrics();
            Rect bounds = windowMetrics.getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
            screenDensity = getResources().getConfiguration().densityDpi;
        } else {
            DisplayMetrics realMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(realMetrics);
            screenWidth = realMetrics.widthPixels;
            screenHeight = realMetrics.heightPixels;
            screenDensity = realMetrics.densityDpi;
        }
        Log.d(TAG, "更新全屏真实分辨率: " + screenWidth + "x" + screenHeight + ", 密度: " + screenDensity);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        blog("SVC: onStartCommand flags=" + flags + " startId=" + startId);
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 需要特殊处理
                startForeground(NOTIFICATION_ID, notification);
            } else {
                // Android 10-12
                startForeground(NOTIFICATION_ID, notification);
            }
        } else {
            // Android 9及以下
            startForeground(NOTIFICATION_ID, notification);
        }

        return START_STICKY;
    }

    public void startRecording(int resultCode, Intent data) {
        if (isRecording) {
            blog("START_BLOCKED: isRecording already true");
            return;
        }
        if (isStopping.get()) {
            blog("START_BLOCKED: stop still in progress");
            return;
        }

        blog("START: begin, w=" + screenWidth + " h=" + screenHeight);
        updateScreenDimensions();
        // 2026/6/4

        // 修改：在开始录制时初始化 NetworkManager
        if (networkManager == null) {
            UserSessionManager session = UserSessionManager.getInstance(this);
            networkManager = new NetworkManager(this, session.getEmail(), session.getToken());
        }

        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null");
            blog("START_FAIL: MediaProjection null");
            return;
        }
        blog("START: got MediaProjection, registering callback");
        // 重置停止标志
        isStopping.set(false);
        capturePipelineGeneration.incrementAndGet();

        // 关键修复：必须在创建 VirtualDisplay 之前注册回调
        mediaProjection.registerCallback(mediaProjectionCallback, handler);

        setupImageReader();
        blog("START: ImageReader setup, size=" + screenWidth + "x" + screenHeight);
        createVirtualDisplay();
        blog("START: VirtualDisplay created");

        isRecording = true;

        LinkedBlockingQueue<CaptureFrame> newFrameQueue = new LinkedBlockingQueue<>(FRAME_QUEUE_CAPACITY);
        frameQueue = newFrameQueue;
        emptyRoundCount = 0;
        lastAcceptedFrameTimeMs = 0L;
        capturePipelineRebuildInFlight.set(false);
        startUploadConsumerThread(newFrameQueue);
        startCaptureControlThread(newFrameQueue);
        blog("START: done, recording began");
    }

    // 2026/6/4
    private void startCaptureControlThread(LinkedBlockingQueue<CaptureFrame> frameQueueRef) {
        Thread worker = new Thread(() -> {
            blog("CAPT: thread started");
            try {
                while(isRecording && !Thread.currentThread().isInterrupted()) {
                    try {
                        long t0 = System.currentTimeMillis();

                        synchronized (captureLock) {
                            requestCleanFrame = true;
                            hasNewFrame = false;
                        }

                        int waitCount = 0;
                        while (!hasNewFrame && waitCount < CAPTURE_WAIT_MAX_ROUNDS) {
                            Thread.sleep(CAPTURE_WAIT_SLICE_MS);
                            waitCount++;
                        }

                        long t1 = System.currentTimeMillis();

                        boolean shouldConvert = false;
                        long timestamp = 0;
                        int pStride = 0, rStride = 0;

                        synchronized (captureLock) {
                            requestCleanFrame = false;
                            if (hasNewFrame && pixelDataBuffer != null) {
                                shouldConvert = true;
                                timestamp = latestFrameTimestamp;
                                pStride = cachedPixelStride;
                                rStride = cachedRowStride;
                            }
                        }

                        if (shouldConvert) {
                            long t2 = System.currentTimeMillis();
                            emptyRoundCount = 0;
                            Bitmap bitmap = buildBitmapFromBuffer(pixelDataBuffer, pStride, rStride);
                            long t3 = System.currentTimeMillis();
                            if (bitmap != null) {
                                CaptureFrame newFrame = new CaptureFrame(bitmap, timestamp);
                                enqueueLatestFrame(frameQueueRef, newFrame);
                                blog("CAPT: frame wait=" + waitCount
                                        + " build=" + (t3-t2) + "ms total=" + (t3-t0) + "ms q=" + frameQueueRef.size());
                            } else {
                                blog("CAPT: buildBitmap null wait=" + waitCount);
                            }
                        } else {
                            emptyRoundCount++;
                            if (emptyRoundCount == 1 || emptyRoundCount % 15 == 0) {
                                blog("CAPT: " + emptyRoundCount + " empty wait=" + waitCount
                                        + " total=" + (t1-t0) + "ms");
                            }
                            maybeTriggerCapturePipelineRebuild("empty_round_" + emptyRoundCount);
                        }
                    } catch (InterruptedException e) {
                        blog("CAPT: INTERRUPTED isRec=" + isRecording
                                + " isStop=" + isStopping.get());
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Throwable t) {
                        Log.e(TAG, "!!!! FATAL ERROR, Capture Thread !!!!", t);
                        blog("CAPT: FATAL " + t.getClass().getSimpleName());
                        if (!isRecording || isStopping.get()) {
                            break;
                        }
                        maybeTriggerCapturePipelineRebuild("capture_fatal_" + t.getClass().getSimpleName());
                        try {
                            Thread.sleep(CAPTURE_RECOVERY_SLEEP_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } finally {
                synchronized (stopLock) {
                    if (captureControlThread == Thread.currentThread()) {
                        captureControlThread = null;
                    }
                }
                tryFinalizeStop();
            }
            blog("CAPT: LOOP EXIT isRec=" + isRecording
                    + " isStop=" + isStopping.get());
        }, "CaptureControlThread");
        captureControlThread = worker;
        worker.start();
    }
    // 2026/6/4

    private void setupImageReader() {
        imageReader = createImageReader();
    }

    private void createVirtualDisplay() {
        virtualDisplay = createVirtualDisplay(imageReader);
    }

    private final ImageReader.OnImageAvailableListener imageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    if (isStopping.get()) return;

                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            synchronized (captureLock) {
                                if (requestCleanFrame) {
                                    try {
                                        Image.Plane[] planes = image.getPlanes();
                                        ByteBuffer buffer = planes[0].getBuffer();
                                        int remaining = buffer.remaining();

                                        if (pixelDataBuffer == null || pixelDataBuffer.length != remaining) {
                                            pixelDataBuffer = new byte[remaining];
                                            blog("IMG: buffer alloc size=" + remaining);
                                        }

                                        buffer.position(0);
                                        buffer.get(pixelDataBuffer);

                                        cachedPixelStride = planes[0].getPixelStride();
                                        cachedRowStride = planes[0].getRowStride();
                                        latestFrameTimestamp = System.currentTimeMillis();
                                        lastAcceptedFrameTimeMs = latestFrameTimestamp;
                                        hasNewFrame = true;
                                        imgCallbackCount++;
                                        if (imgCallbackCount % 10 == 0) {
                                            blog("IMG: onAvailable #" + imgCallbackCount
                                                + " stride=" + cachedPixelStride + "x" + cachedRowStride);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error copying pixels: " + e.getMessage());
                                        blog("IMG: copy error " + e.getMessage());
                                    }
                                }
                            }
                        } else {
                            blog("IMG: acquireLatestImage null");
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "!!!! FATAL ERROR, Image Callback !!!!", t);
                        blog("IMG: callback fatal " + t.getClass().getSimpleName());
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
            };
    //2026/6/4
    private Bitmap buildBitmapFromBuffer(byte[] pixels, int pixelStride, int rowStride) {
        Bitmap uploadBitmap = null;
        try {
            int rowPadding = rowStride - pixelStride * screenWidth;
            int paddedWidth = screenWidth + rowPadding / pixelStride;
            if (paddedWidth <= 0 || screenHeight <= 0) {
                blog("IMG: invalid bitmap size paddedWidth=" + paddedWidth + " height=" + screenHeight);
                return null;
            }

            ensureReusableBitmaps(pixels, pixelStride, rowStride, paddedWidth);

            reusablePixelBuffer.position(0);
            reusablePixelBuffer.limit(pixels.length);
            reusablePaddedBitmap.copyPixelsFromBuffer(reusablePixelBuffer);

            uploadBitmap = Bitmap.createBitmap(
                    screenWidth,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );
            Canvas uploadCanvas = new Canvas(uploadBitmap);
            uploadCanvas.drawBitmap(
                    reusablePaddedBitmap,
                    reusableCropRect,
                    reusableCropRect,
                    null
            );
            return uploadBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error building bitmap: " + e.getMessage());
            blog("IMG: error building bitmap");
            recycleFrameBitmap(uploadBitmap);
            return null;
        }
    }
    //2026/6/4

    private void ensureReusableBitmaps(byte[] pixels, int pixelStride, int rowStride, int paddedWidth) {
        boolean needRecreate =
                reusablePaddedBitmap == null
                        || reusableCropRect == null
                        || reusablePaddedBitmap.isRecycled()
                        || reusablePaddedWidth != paddedWidth
                        || reusablePixelStride != pixelStride
                        || reusableRowStride != rowStride
                        || reusableCropRect.right != screenWidth
                        || reusableCropRect.bottom != screenHeight;

        if (needRecreate) {
            releaseReusableBitmaps();
            reusablePaddedBitmap = Bitmap.createBitmap(
                    paddedWidth,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );
            reusableCropRect = new Rect(0, 0, screenWidth, screenHeight);
            reusablePaddedWidth = paddedWidth;
            reusablePixelStride = pixelStride;
            reusableRowStride = rowStride;
            blog("IMG: reusable bitmap alloc padded=" + paddedWidth + "x" + screenHeight);
        }

        if (reusablePixelBuffer == null || reusablePixelBuffer.array() != pixels) {
            reusablePixelBuffer = ByteBuffer.wrap(pixels);
        }
    }

    private boolean isReusableBitmap(Bitmap bitmap) {
        return bitmap != null && bitmap == reusablePaddedBitmap;
    }

    private void recycleFrameBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled() && !isReusableBitmap(bitmap)) {
            bitmap.recycle();
        }
    }

    private void releaseReusableBitmaps() {
        if (reusablePaddedBitmap != null && !reusablePaddedBitmap.isRecycled()) {
            reusablePaddedBitmap.recycle();
        }
        reusablePaddedBitmap = null;
        reusablePixelBuffer = null;
        reusableCropRect = null;
        reusablePaddedWidth = 0;
        reusablePixelStride = 0;
        reusableRowStride = 0;
    }

    private void startUploadConsumerThread(LinkedBlockingQueue<CaptureFrame> frameQueueRef) {
        final NetworkManager uploadNetworkManager = networkManager;
        Thread worker = new Thread(() -> {
            Log.e(TAG, "Upload Thread is running..");
            blog("UPLOAD: thread started");
            if (uploadNetworkManager == null) {
                blog("UPLOAD: network manager missing");
                return;
            }
            try {
                while(isRecording && !Thread.currentThread().isInterrupted())
                {
                    try{
                    CaptureFrame frame = frameQueueRef.take();
                    long tUp0 = System.currentTimeMillis();

                    if(frame != null && frame.bitmap != null)
                    {
                        Bitmap bitmap = frame.bitmap;
                        long timestamp = frame.captureTimeMs;

                        try {
                            blog("UPLOAD: now sending image...");
                            String response = uploadNetworkManager.uploadImage(bitmap,timestamp);
                            long tUp1 = System.currentTimeMillis();
                            /*String preview = response != null && response.length() > 80
                                ? response.substring(0, 80) + "..." : response;*/
                            blog("UPLOAD: OK " + (tUp1-tUp0) + "ms lat="
                                + (tUp1-timestamp));
                            if (timestamp < latestDisplayedFrameTimeMs) {
                                blog("UPLOAD: stale response dropped frameTs=" + timestamp
                                        + " latestDisplayed=" + latestDisplayedFrameTimeMs);
                            } else {
                                latestDisplayedFrameTimeMs = timestamp;
                                updateFloatingWindowWithResultPayload(response);
                            }
                        } catch (Exception e)
                        {
                            Log.e(TAG, "Error uploading image: " + e.getMessage());
                            blog("UPLOAD: FAIL " + e.getMessage());
                            updateFloatingWindow("上传失败: " + e.getMessage());
                        } finally {
                            recycleFrameBitmap(bitmap);
                        }
                    }
                    } catch (InterruptedException e)
                    {
                        blog("UPLOAD: INTERRUPTED isRec=" + isRecording
                                + " q=" + frameQueueRef.size());
                        Thread.currentThread().interrupt();
                        break;
                    }
                    catch (Throwable t) {
                        Log.e(TAG, "!!!! FATAL ERROR, Upload Thread !!!!", t);
                        blog("UPLOAD: FATAL " + t.getClass().getSimpleName());
                    }
                }
            } finally {
                synchronized (stopLock) {
                    if (uploadThread == Thread.currentThread()) {
                        uploadThread = null;
                    }
                }
                tryFinalizeStop();
            }
            Log.e(TAG, "==== upload thread terminate ====");
            blog("UPLOAD: LOOP EXIT isRec=" + isRecording
                    + " q=" + frameQueueRef.size());
        }, "NetworkUploadThread");
        uploadThread = worker;
        worker.start();
    }

    private void updateFloatingWindow(String text) {
        if (text != null && text.length() > 100) {
            blog("FLOAT: " + text.substring(0, 100) + "...");
        } else {
            blog("FLOAT: " + text);
        }
        Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_TEXT);
        intent.setPackage(getPackageName());
        intent.putExtra(FloatingWindowService.EXTRA_TEXT, text);
        sendBroadcast(intent);
    }

    private void updateFloatingWindowWithResultPayload(String payload) {
        updateFloatingWindow("__RESULT__:" + payload);
    }

    public void stopRecording() {
        if (!isRecording && !isStopping.get()) {
            return;
        }
        if (isStopping.get()) {
            blog("STOP: retry finalize");
            tryFinalizeStop();
            return;
        }
        blog("STOP: called isRec=" + isRecording + " isStop=" + isStopping.get());
        isStopping.set(true);
        capturePipelineGeneration.incrementAndGet();
        isRecording = false;
        requestCleanFrame = false;
        hasNewFrame = false;

        updateFloatingWindow("录制已停止，等待开始...");

        Thread captureThreadToJoin = captureControlThread;
        Thread uploadThreadToJoin = uploadThread;

        if(captureThreadToJoin != null) {
            blog("STOP: interrupting capt thread");
            captureThreadToJoin.interrupt();
        }
        // 停止消费者线程
        if (uploadThreadToJoin != null) {
            blog("STOP: interrupting upload thread");
            uploadThreadToJoin.interrupt();
        }
        if (networkManager != null) {
            networkManager.cancelInFlightUpload();
        }

        joinThread(captureThreadToJoin, "CaptureControlThread", 2000);
        joinThread(uploadThreadToJoin, "NetworkUploadThread", 4000);
        tryFinalizeStop();
    }

    private boolean joinThread(Thread thread, String name, long timeoutMs) {
        if (thread == null) {
            return true;
        }
        try {
            thread.join(timeoutMs);
            if (thread.isAlive()) {
                blog("STOP: " + name + " still alive after " + timeoutMs + "ms");
                return false;
            } else {
                blog("STOP: " + name + " joined");
                return true;
            }
        } catch (InterruptedException e) {
            blog("STOP: join interrupted for " + name);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void tryFinalizeStop() {
        synchronized (stopLock) {
            if (!isStopping.get()) {
                return;
            }
            if (captureControlThread != null || uploadThread != null) {
                updateFloatingWindow("录制停止中，请稍候再试...");
                blog("STOP: finalize deferred capture=" + (captureControlThread != null)
                        + " upload=" + (uploadThread != null));
                return;
            }

            // 【修改】清理 latestCleanFrame
            if (latestCleanFrame != null) {
                recycleFrameBitmap(latestCleanFrame.bitmap);
                latestCleanFrame = null;
            }

            LinkedBlockingQueue<CaptureFrame> queueToClear = frameQueue;
            frameQueue = null;

            blog("STOP: releasing resources, qSize=" + (queueToClear != null ? queueToClear.size() : -1));

            if (queueToClear != null) {
                while (!queueToClear.isEmpty()) {
                    CaptureFrame f = queueToClear.poll();
                    if (f != null) {
                        recycleFrameBitmap(f.bitmap);
                    }
                }
                queueToClear.clear();
            }

            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }

            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }

            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }

            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }

            if (networkManager != null) {
                networkManager = null;
            }
            pixelDataBuffer = null;
            releaseReusableBitmaps();
            emptyRoundCount = 0;
            lastAcceptedFrameTimeMs = 0L;
            latestEnqueuedFrameTimeMs = 0L;
            latestDisplayedFrameTimeMs = 0L;
            capturePipelineRebuildInFlight.set(false);
            isStopping.set(false);
            blog("STOP: done");
        }
    }

    private void enqueueLatestFrame(LinkedBlockingQueue<CaptureFrame> frameQueueRef, CaptureFrame newFrame) {
        latestEnqueuedFrameTimeMs = newFrame.captureTimeMs;
        while (!frameQueueRef.isEmpty()) {
            CaptureFrame oldFrame = frameQueueRef.poll();
            if (oldFrame != null) {
                recycleFrameBitmap(oldFrame.bitmap);
            }
        }
        if (!frameQueueRef.offer(newFrame)) {
            recycleFrameBitmap(newFrame.bitmap);
            blog("CAPT: queue full, dropped frame");
        }
    }

    private void maybeTriggerCapturePipelineRebuild(String reason) {
        if (!isRecording || isStopping.get() || mediaProjection == null || handler == null) {
            return;
        }
        if (emptyRoundCount < EMPTY_ROUNDS_BEFORE_REBUILD) {
            return;
        }
        if (!capturePipelineRebuildInFlight.compareAndSet(false, true)) {
            return;
        }
        final long sinceLastAccepted = lastAcceptedFrameTimeMs == 0L
                ? -1L
                : (System.currentTimeMillis() - lastAcceptedFrameTimeMs);
        final int expectedGeneration = capturePipelineGeneration.get();
        blog("CAPT: schedule pipeline rebuild reason=" + reason
                + " empty=" + emptyRoundCount
                + " lastAcceptedGap=" + sinceLastAccepted + "ms"
                + " gen=" + expectedGeneration);
        handler.post(() -> rebuildCapturePipeline(reason, expectedGeneration));
    }

    private boolean isCapturePipelineGenerationValidLocked(int expectedGeneration) {
        return expectedGeneration == capturePipelineGeneration.get()
                && isRecording
                && !isStopping.get()
                && mediaProjection != null;
    }

    private void closeImageReaderQuietly(ImageReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (Throwable t) {
            Log.w(TAG, "Error closing ImageReader", t);
        }
    }

    private void releaseVirtualDisplayQuietly(VirtualDisplay display) {
        if (display == null) {
            return;
        }
        try {
            display.release();
        } catch (Throwable t) {
            Log.w(TAG, "Error releasing VirtualDisplay", t);
        }
    }

    private ImageReader createImageReader() {
        ImageReader reader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                android.graphics.PixelFormat.RGBA_8888,
                2
        );
        reader.setOnImageAvailableListener(imageAvailableListener, handler);
        return reader;
    }

    private VirtualDisplay createVirtualDisplay(ImageReader targetReader) {
        return mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                targetReader.getSurface(),
                null,
                handler
        );
    }

    private void rebuildCapturePipeline(String reason, int expectedGeneration) {
        ImageReader newImageReader = null;
        VirtualDisplay newVirtualDisplay = null;
        try {
            synchronized (stopLock) {
                if (!isCapturePipelineGenerationValidLocked(expectedGeneration)) {
                    blog("CAPT: rebuild skipped before release reason=" + reason
                            + " gen=" + expectedGeneration
                            + " currentGen=" + capturePipelineGeneration.get());
                    return;
                }

                blog("CAPT: rebuilding pipeline reason=" + reason
                        + " gen=" + expectedGeneration);
                releaseVirtualDisplayQuietly(virtualDisplay);
                virtualDisplay = null;
                closeImageReaderQuietly(imageReader);
                imageReader = null;

                if (!isCapturePipelineGenerationValidLocked(expectedGeneration)) {
                    blog("CAPT: rebuild aborted after release reason=" + reason
                            + " gen=" + expectedGeneration
                            + " currentGen=" + capturePipelineGeneration.get());
                    return;
                }

                newImageReader = createImageReader();
                if (!isCapturePipelineGenerationValidLocked(expectedGeneration)) {
                    blog("CAPT: rebuild aborted after reader create reason=" + reason
                            + " gen=" + expectedGeneration
                            + " currentGen=" + capturePipelineGeneration.get());
                    return;
                }

                newVirtualDisplay = createVirtualDisplay(newImageReader);
                if (!isCapturePipelineGenerationValidLocked(expectedGeneration)) {
                    blog("CAPT: rebuild aborted after display create reason=" + reason
                            + " gen=" + expectedGeneration
                            + " currentGen=" + capturePipelineGeneration.get());
                    return;
                }

                imageReader = newImageReader;
                newImageReader = null;
                virtualDisplay = newVirtualDisplay;
                newVirtualDisplay = null;
                synchronized (captureLock) {
                    requestCleanFrame = false;
                    hasNewFrame = false;
                }
                emptyRoundCount = 0;
                imgCallbackCount = 0;
                blog("CAPT: rebuild done gen=" + expectedGeneration);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error rebuilding capture pipeline", t);
            blog("CAPT: rebuild fail " + t.getClass().getSimpleName()
                    + " gen=" + expectedGeneration);
        } finally {
            releaseVirtualDisplayQuietly(newVirtualDisplay);
            closeImageReaderQuietly(newImageReader);
            capturePipelineRebuildInFlight.set(false);
        }
    }

    private final MediaProjection.Callback mediaProjectionCallback =
            new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    blog("MDP_ONSTOP: system reclaimed MediaProjection"
                        + " isRec=" + isRecording);
                    stopRecording();
                }
            };

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "屏幕录制服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("后台屏幕录制服务正在运行");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕录制服务")
                .setContentText("正在后台录制屏幕")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        blog("SVC: onBind");
        return binder;
    }

    public boolean isRecording() {
        return isRecording;
    }

    @Override
    public void onDestroy() {
        blog("SVC: onDestroy");
        stopRecording();
        if (handlerThread != null) {
            handlerThread.quitSafely();
            joinThread(handlerThread, "ScreenCapture", 2000);
            handlerThread = null;
            handler = null;
        }
        super.onDestroy();
    }
}
