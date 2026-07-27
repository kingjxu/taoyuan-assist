package com.example.screencaptureapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.LinkedBlockingQueue;
import android.view.WindowManager;
import android.view.WindowMetrics;
import java.util.concurrent.Semaphore;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final int NOTIFICATION_ID = 1001;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread handlerThread;
    private Handler handler;

    private int screenDensity;
    private int screenWidth;
    private int screenHeight;

    private volatile boolean isRecording = false;
    private LinkedBlockingQueue<CaptureFrame> frameQueue;
    private Thread uploadThread;

    private final AtomicBoolean isStopping = new AtomicBoolean(false);

    private final IBinder binder = new LocalBinder();
    private NetworkManager networkManager;

    // todo 1 start
    private Thread captureControlThread;
    private final Object captureLock = new Object();
    private volatile boolean requestCleanFrame = false;
    private CaptureFrame latestCleanFrame = null;
    // todo 1 end

    // 新增：用于缓存纯像素数据的复用数组，避免频繁创建对象
    // 2026/6/4
    private byte[] pixelDataBuffer;
    private int cachedPixelStride;
    private int cachedRowStride;
    private long latestFrameTimestamp;
    private volatile boolean hasNewFrame = false;
    private final Semaphore cycleSync = new Semaphore(1);
    private int emptyRoundCount = 0;
    private int imgCallbackCount = 0;

    // 自建日志环形缓冲 + logcat + 每秒定时上报到 ddbaby.site（静态，全App共享）
    private static final StringBuilder logBuf = new StringBuilder();
    private ScheduledExecutorService periodicUploader;
    private long uploadSeq = 0;

    /** 供任意类写诊断日志（同时写内存缓冲和 logcat） */
    public static void blog(String msg) {
        long ts = System.currentTimeMillis();
        String line = ts + "|" + msg;
        Log.d("GTO", line);
        synchronized (logBuf) {
            logBuf.append(line).append("\n");
            if (logBuf.length() > 20000) logBuf.delete(0, logBuf.length() - 15000);
        }
    }

    /** 启动每秒定时上报 */
    private void startPeriodicUpload() {
        if (periodicUploader != null) return;
        periodicUploader = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GTO-LogUpload");
            t.setDaemon(true);
            return t;
        });
        periodicUploader.scheduleAtFixedRate(() -> {
            uploadSeq++;
            doUpload("periodic#" + uploadSeq);
        }, 1, 1, TimeUnit.SECONDS);
    }

    /** 停止定时上报 + 最后刷一次 */
    private void stopPeriodicUpload() {
        if (periodicUploader == null) return;
        // 最后一次立即刷出
        doUpload("final");
        periodicUploader.shutdown();
        periodicUploader = null;
    }

    private void doUpload(String trigger) {
        final String dump;
        synchronized (logBuf) { dump = logBuf.toString(); }
        if (dump.isEmpty()) return;
        UserSessionManager sess = UserSessionManager.getInstance(this);
        final String email = sess.getEmail();
        final String token = sess.getToken();
        try {
            okhttp3.OkHttpClient cli = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build();
            okhttp3.MediaType jsonType = okhttp3.MediaType.parse("application/json; charset=utf-8");
            com.google.gson.JsonObject body = new com.google.gson.JsonObject();
            body.addProperty("uuid", email);
            body.addProperty("trigger", trigger);
            body.addProperty("device", Build.MODEL);
            body.addProperty("log", dump);
            okhttp3.RequestBody rb = okhttp3.RequestBody.create(jsonType, body.toString());
            okhttp3.Request req = new okhttp3.Request.Builder()
                .url("http://ddbaby.site/lyxz/gto_debug_log")
                .post(rb)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + (token != null ? token : ""))
                .build();
            okhttp3.Response resp = cli.newCall(req).execute();
            Log.d(TAG, "log upload " + (resp.isSuccessful() ? "OK" : "FAIL " + resp.code()) + " " + trigger);
            resp.close();
            // 上报成功 → 清掉已发送的日志，避免下次重复
            synchronized (logBuf) { logBuf.delete(0, dump.length()); }
        } catch (Exception e) {
            Log.e(TAG, "log upload err " + trigger, e);
            // 上报失败 → 保留缓冲区，下次重试
        }
    }
    // 2026/6/3 start
    public static class CaptureFrame {
        public final Bitmap bitmap;
        public final long captureTimeMs; // 截取该帧的具体时间戳(毫秒)

        public CaptureFrame(Bitmap bitmap, long captureTimeMs) {
            this.bitmap = bitmap;
            this.captureTimeMs = captureTimeMs;
        }
    }
    // 2026/6/3 end

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

    /**
     * 核心修复方法：获取包含状态栏、导航栏、挖孔区的全屏真实物理分辨率
     */
    private void updateScreenDimensions() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        // 【新增校验】如果获取不到 WindowManager，直接抛出异常让 App 崩溃并停止运行
        if (windowManager == null) {
            throw new IllegalStateException("Critical Error: WindowManager is null. Cannot retrieve screen dimensions. Terminating application.");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 标准物理屏幕区域获取
            WindowMetrics windowMetrics = windowManager.getMaximumWindowMetrics();
            Rect bounds = windowMetrics.getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
            screenDensity = getResources().getConfiguration().densityDpi;
        } else {
            // Android 11 以下利用反射/真实指标接口获取
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

        // 处理不同Android版本的前台服务启动
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

        // 关键修复：必须在创建 VirtualDisplay 之前注册回调
        mediaProjection.registerCallback(mediaProjectionCallback, handler);

        setupImageReader();
        blog("START: ImageReader setup, size=" + screenWidth + "x" + screenHeight);
        createVirtualDisplay();
        blog("START: VirtualDisplay created");

        isRecording = true;

        frameQueue = new LinkedBlockingQueue<>(3);
        startUploadConsumerThread();
        startCaptureControlThread();
        startPeriodicUpload();
        blog("START: done, recording began");
    }

    // 2026/6/4
    private void startCaptureControlThread() {
        captureControlThread = new Thread(() -> {
            blog("CAPT: thread started");
            while(isRecording && !Thread.currentThread().isInterrupted()) {
                try {
                    //2026/6/5
                    cycleSync.acquire();
                    //2026/6/5

                    long t0 = System.currentTimeMillis();

                    // --- 1. 开启采集窗口 ---
                    synchronized (captureLock) {
                        requestCleanFrame = true;
                        hasNewFrame = false;
                    }

                    int waitCount = 0;
                    while (!hasNewFrame && waitCount < 10) {
                        Thread.sleep(50);
                        waitCount++;
                    }

                    long t1 = System.currentTimeMillis();

                    // --- 2. 结束采集窗口，获取最终参数 ---
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

                    // --- 3. 在锁外执行唯一一次耗时的 Bitmap 转换 ---
                    if (shouldConvert) {
                        long t2 = System.currentTimeMillis();
                        emptyRoundCount = 0;
                        Bitmap bitmap = buildBitmapFromBuffer(pixelDataBuffer, pStride, rStride);
                        long t3 = System.currentTimeMillis();
                        if (bitmap != null) {
                            CaptureFrame newFrame = new CaptureFrame(bitmap, timestamp);
                            while(!frameQueue.isEmpty()) {
                                CaptureFrame oldFrame = frameQueue.poll();
                                if(oldFrame != null && oldFrame.bitmap != null && !oldFrame.bitmap.isRecycled()) {
                                    oldFrame.bitmap.recycle();
                                }
                            }
                            frameQueue.offer(newFrame);
                            blog("CAPT: frame wait=" + waitCount
                                + " build=" + (t3-t2) + "ms total=" + (t3-t0) + "ms q=" + frameQueue.size());
                        }
                        else
                        {
                            blog("CAPT: buildBitmap null wait=" + waitCount);
                            cycleSync.release();
                        }
                    }
                    else {
                        emptyRoundCount++;
                        if (emptyRoundCount == 1 || emptyRoundCount % 15 == 0) {
                            blog("CAPT: " + emptyRoundCount + " empty wait=" + waitCount
                                + " total=" + (t1-t0) + "ms");
                        }
                        cycleSync.release();
                    }

                } catch (InterruptedException e) {
                    blog("CAPT: INTERRUPTED isRec=" + isRecording
                            + " isStop=" + isStopping.get()
                            + " perm=" + cycleSync.availablePermits());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            blog("CAPT: LOOP EXIT isRec=" + isRecording
                    + " isStop=" + isStopping.get());
        }, "CaptureControlThread");
        captureControlThread.start();
    }
    // 2026/6/4

    private void setupImageReader() {
        imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                android.graphics.PixelFormat.RGBA_8888,
                2
        );

        imageReader.setOnImageAvailableListener(imageAvailableListener, handler);
    }

    private void createVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler
        );
    }

    private final ImageReader.OnImageAvailableListener imageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    if (isStopping.get()) return;

                    Image image = reader.acquireLatestImage();
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
                        // 无论是否采纳，必须极速释放 Image，防止底层阻塞
                        image.close();
                    } else {
                        blog("IMG: acquireLatestImage null");
                    }
                }
            };
    //2026/6/4
    private Bitmap buildBitmapFromBuffer(byte[] pixels, int pixelStride, int rowStride) {
        try {
            int rowPadding = rowStride - pixelStride * screenWidth;
            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));

            Bitmap croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    screenWidth,
                    screenHeight
            );
            bitmap.recycle();
            return croppedBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error building bitmap: " + e.getMessage());
            blog("IMG: error building bitmap");
            return null;
        }
    }
    //2026/6/4

    private void startUploadConsumerThread() {
        uploadThread = new Thread(() -> {
            Log.e(TAG, "Upload Thread is running..");
            blog("UPLOAD: thread started");
            while(isRecording && !Thread.currentThread().isInterrupted())
            {
                try{
                    CaptureFrame frame = frameQueue.take();
                    long tUp0 = System.currentTimeMillis();

                    if(frame != null && frame.bitmap != null)
                    {
                        Bitmap bitmap = frame.bitmap;
                        long timestamp = frame.captureTimeMs;

                        try {
                            blog("UPLOAD: now sending image...");
                            String response = networkManager.uploadImage(bitmap,timestamp);
                            long tUp1 = System.currentTimeMillis();
                            /*String preview = response != null && response.length() > 80
                                ? response.substring(0, 80) + "..." : response;*/
                            blog("UPLOAD: OK " + (tUp1-tUp0) + "ms lat="
                                + (tUp1-timestamp));
                            updateFloatingWindow(response);
                        } catch (Exception e)
                        {
                            Log.e(TAG, "Error uploading image: " + e.getMessage());
                            blog("UPLOAD: FAIL " + e.getMessage());
                            updateFloatingWindow("上传失败: " + e.getMessage());
                        } finally {
                            if(!bitmap.isRecycled())
                            {
                                bitmap.recycle();
                            }

                            //2026/6/5
                            Thread.sleep(1000);
                            cycleSync.release();
                            //2026/6/5
                        }
                    }
                } catch (InterruptedException e)
                {
                    blog("UPLOAD: INTERRUPTED isRec=" + isRecording
                            + " perm=" + cycleSync.availablePermits());
                    Thread.currentThread().interrupt();
                    break;
                }
                catch (Throwable t) {
                    Log.e(TAG, "!!!! FATAL ERROR, Upload Thread !!!!", t);
                    blog("UPLOAD: FATAL " + t.getClass().getSimpleName());
                    //2026/6/5
                    cycleSync.release();
                    //2026/6/5
                }
            }
            Log.e(TAG, "==== upload thread terminate ====");
            blog("UPLOAD: LOOP EXIT isRec=" + isRecording
                    + " perm=" + cycleSync.availablePermits());
        }, "NetworkUploadThread");
        uploadThread.start();
    }

    private void updateFloatingWindow(String text) {
        if (text != null && text.length() > 100) {
            blog("FLOAT: " + text.substring(0, 100) + "...");
        } else {
            blog("FLOAT: " + text);
        }
        Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_TEXT);
        intent.putExtra(FloatingWindowService.EXTRA_TEXT, text);
        sendBroadcast(intent);
    }

    public void stopRecording() {
        if (!isRecording || isStopping.get()) {
            return;
        }
        blog("STOP: called isRec=" + isRecording + " isStop=" + isStopping.get());
        isStopping.set(true);
        isRecording = false;

        //stopPeriodicUpload();

        updateFloatingWindow("录制已停止，等待开始...");

        if(captureControlThread != null) {
            blog("STOP: interrupting capt thread");
            captureControlThread.interrupt();
            captureControlThread = null;
        }
        // 停止消费者线程
        if (uploadThread != null) {
            blog("STOP: interrupting upload thread");
            uploadThread.interrupt();
            uploadThread = null;
        }

        // 【修改】清理 latestCleanFrame
        if(latestCleanFrame != null && latestCleanFrame.bitmap != null && !latestCleanFrame.bitmap.isRecycled())
        {
            latestCleanFrame.bitmap.recycle();
            latestCleanFrame = null;
        }
        requestCleanFrame = false;
        blog("STOP: releasing resources, qSize=" + (frameQueue != null ? frameQueue.size() : -1));

        // 清空队列，释放未处理的 Bitmap 防止内存泄漏
        if (frameQueue != null) {
            while (!frameQueue.isEmpty()) {
                CaptureFrame f = frameQueue.poll();
                if (f != null && f.bitmap != null && !f.bitmap.isRecycled()) {
                    f.bitmap.recycle();
                }
            }
            frameQueue.clear();
        }

        // 移除所有未执行的回调
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

        if (handlerThread != null) {
            handlerThread.quitSafely();
        }

        // 修改：清理 networkManager
        if (networkManager != null) {
            networkManager = null;
        }
        // 2026/6/4
        pixelDataBuffer = null;
        emptyRoundCount = 0;
        // 2026/6/4
        isStopping.set(false);
        blog("STOP: done");
    }

    private final MediaProjection.Callback mediaProjectionCallback =
            new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    blog("MDP_ONSTOP: system reclaimed MediaProjection"
                        + " isRec=" + isRecording
                        + " perm=" + cycleSync.availablePermits());
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
        stopPeriodicUpload();
        super.onDestroy();
        stopRecording();
    }
}
