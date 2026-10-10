package com.example.screencaptureapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FloatingWindowService extends Service {
    public static final String ACTION_HIDE_WINDOW = "HIDE_WINDOW";
    public static final String ACTION_SHOW_WINDOW = "SHOW_WINDOW";
    public static final String ACTION_UPDATE_TEXT = "UPDATE_TEXT";
    private static final String CHANNEL_ID = "floating_window_channel";
    private static final long CLEAR_TEXT_DELAY_MS = 5000;
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_TRACE_ID = "trace_id";
    private static final int MAX_DISPLAY_CHARS = 120;
    private static final int NOTIFICATION_ID = 1002;
    private static final String RESULT_PREFIX = "__RESULT__:";
    private static final String TAG = "FloatingWindowService";
    private TextView feedbackText;
    private View floatingView;
    private Handler handler;
    private WindowManager windowManager;
    private boolean receiverRegistered;
    private boolean startupFailed;
    private final Runnable clearTextRunnable = new Runnable() {
        @Override
        public void run() {
            if (FloatingWindowService.this.feedbackText != null) {
                FloatingWindowService.this.feedbackText.setText("");
                ScreenCaptureService.blog("FLOAT: text cleared");
            }
        }
    };
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (FloatingWindowService.ACTION_UPDATE_TEXT.equals(intent.getAction())) {
                String stringExtra = intent.getStringExtra(FloatingWindowService.EXTRA_TEXT);
                if (stringExtra != null) {
                    FloatingWindowService.this.updateFeedbackText(
                            stringExtra,
                            intent.getStringExtra(FloatingWindowService.EXTRA_TRACE_ID)
                    );
                    return;
                }
                return;
            }
            if (FloatingWindowService.ACTION_HIDE_WINDOW.equals(intent.getAction())) {
                if (FloatingWindowService.this.floatingView != null) {
                    FloatingWindowService.this.floatingView.setVisibility(4);
                }
            } else {
                if (!FloatingWindowService.ACTION_SHOW_WINDOW.equals(intent.getAction()) || FloatingWindowService.this.floatingView == null) {
                    return;
                }
                FloatingWindowService.this.floatingView.setVisibility(0);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        this.windowManager = (WindowManager) getSystemService("window");
        this.handler = new Handler(getMainLooper());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UPDATE_TEXT);
        intentFilter.addAction(ACTION_HIDE_WINDOW);
        intentFilter.addAction(ACTION_SHOW_WINDOW);
        try {
            ContextCompat.registerReceiver(this, updateReceiver, intentFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        } catch (RuntimeException e) {
            startupFailed = true;
            OverlayDiagnostics.report(this, "悬浮窗服务初始化失败，请停止录制后重试。", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (startupFailed) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (RuntimeException e) {
            OverlayDiagnostics.report(this, "系统未能启动悬浮窗前台服务，请检查应用权限后重新开始录制。", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!createFloatingWindow()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private boolean createFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) {
            OverlayDiagnostics.report(this, "悬浮窗权限未开启，无法显示等待反馈或识别结果。请到系统设置允许悬浮窗，再重新开始录制。", null);
            return false;
        }
        if (floatingView != null) return true;
        try {
            View candidate = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
            TextView candidateText = candidate.findViewById(R.id.tv_feedback);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.x = 20;
            params.y = 50;
            params.windowAnimations = 0;
            makeDraggable(candidate, params);
            windowManager.addView(candidate, params);
            // Publish only after addView succeeds, so a failed attempt is retryable.
            floatingView = candidate;
            feedbackText = candidateText;
            OverlayDiagnostics.clear(this);
            Log.d(TAG, "Floating window created");
            return true;
        } catch (RuntimeException e) {
            floatingView = null;
            feedbackText = null;
            OverlayDiagnostics.report(this, "悬浮窗创建失败。请检查系统的悬浮窗限制；若已允许，请停止录制后重试。", e);
            return false;
        }
    }

    private void makeDraggable(View view, WindowManager.LayoutParams params) {
        int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        view.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int downWindowX;
            private int downWindowY;
            private boolean dragging;

            @Override
            public boolean onTouch(View touchedView, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downWindowX = params.x;
                        downWindowY = params.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;
                        if (!dragging && Math.hypot(deltaX, deltaY) < touchSlop) {
                            return true;
                        }
                        dragging = true;
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int screenHeight = getResources().getDisplayMetrics().heightPixels;
                        int horizontalLimit = Math.max(0,
                                (screenWidth - touchedView.getWidth()) / 2);
                        int bottomLimit = Math.max(0,
                                screenHeight - touchedView.getHeight());
                        params.x = Math.max(-horizontalLimit, Math.min(horizontalLimit,
                                downWindowX + Math.round(deltaX)));
                        params.y = Math.max(0, Math.min(bottomLimit,
                                downWindowY + Math.round(deltaY)));
                        try {
                            windowManager.updateViewLayout(touchedView, params);
                        } catch (RuntimeException e) {
                            Log.w(TAG, "Unable to move floating window", e);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            touchedView.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    public void updateFeedbackText(String str) {
        updateFeedbackText(str, null);
    }

    private void updateFeedbackText(String str, String traceId) {
        if (str == null || str.isEmpty()) {
            return;
        }
        String strResolveDisplayText = resolveDisplayText(str);
        if (strResolveDisplayText == null || strResolveDisplayText.isEmpty()) {
            ScreenCaptureService.blog("FLOAT: display text empty, skip");
            LatencyTraceStore.discard(traceId);
            return;
        }
        if (!createFloatingWindow()) {
            LatencyTraceStore.discard(traceId);
            stopSelf();
            return;
        }
        if (strResolveDisplayText.length() > MAX_DISPLAY_CHARS) {
            strResolveDisplayText = strResolveDisplayText.substring(0, MAX_DISPLAY_CHARS) + "...";
        }
        TextView textView = this.feedbackText;
        if (textView != null) {
            textView.setText(strResolveDisplayText);
            ScreenCaptureService.blog("FLOAT: displayed trace=" + traceId);
        }
        View view = this.floatingView;
        if (view != null && view.getVisibility() != 0) {
            this.floatingView.setVisibility(0);
        }
        this.handler.removeCallbacks(this.clearTextRunnable);
        this.handler.postDelayed(this.clearTextRunnable, CLEAR_TEXT_DELAY_MS);
        if (traceId != null && this.floatingView != null) {
            this.floatingView.postOnAnimation(() -> LatencyTraceStore.markDisplayed(traceId));
        }
    }

    static String resolveDisplayText(String str) {
        // Only server Result text may replace the initial waiting placeholder.
        if (str == null || !str.startsWith(RESULT_PREFIX)) {
            return null;
        }
        try {
            JsonElement string = JsonParser.parseString(str.substring(RESULT_PREFIX.length()));
            if (!string.isJsonObject()) {
                return null;
            }
            JsonObject asJsonObject = string.getAsJsonObject();
            JsonElement result = asJsonObject.get("Result");
            if (result != null && result.isJsonPrimitive()
                    && result.getAsJsonPrimitive().isString()) {
                String text = result.getAsString().trim();
                return text.isEmpty() ? null : text;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, "悬浮窗服务", 2);
        notificationChannel.setDescription("显示屏幕录制反馈");
        ((NotificationManager) getSystemService(NotificationManager.class)).createNotificationChannel(notificationChannel);
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("屏幕录制反馈").setContentText("显示录制状态反馈").setSmallIcon(android.R.drawable.ic_dialog_info).setPriority(-1).build();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(clearTextRunnable);
        if (receiverRegistered) unregisterReceiver(updateReceiver);
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (RuntimeException e) {
                Log.w(TAG, "Overlay already removed by system", e);
            }
        }
        floatingView = null;
        feedbackText = null;
        super.onDestroy();
    }
}
