package com.example.screencaptureapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
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
    private static final int MAX_DISPLAY_CHARS = 120;
    private static final int NOTIFICATION_ID = 1002;
    private static final String RESULT_PREFIX = "__RESULT__:";
    private static final String TAG = "FloatingWindowService";
    private TextView feedbackText;
    private View floatingView;
    private Handler handler;
    private WindowManager windowManager;
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
                    FloatingWindowService.this.updateFeedbackText(stringExtra);
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
        createNotificationChannel();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UPDATE_TEXT);
        intentFilter.addAction(ACTION_HIDE_WINDOW);
        intentFilter.addAction(ACTION_SHOW_WINDOW);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.updateReceiver, intentFilter, 4);
        } else {
            registerReceiver(this.updateReceiver, intentFilter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int i, int i2) {
        Log.d(TAG, "Service onStartCommand");
        startForeground(1002, createNotification());
        if (this.floatingView != null) {
            return 1;
        }
        createFloatingWindow();
        return 1;
    }

    private void createFloatingWindow() {
        View viewInflate = LayoutInflater.from(this).inflate(R.layout.floating_window, (ViewGroup) null);
        this.floatingView = viewInflate;
        this.feedbackText = (TextView) viewInflate.findViewById(R.id.tv_feedback);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(-2, -2, 2038, 296, -3);
        layoutParams.gravity = 49;
        layoutParams.x = 20;
        layoutParams.y = 100;
        layoutParams.windowAnimations = 0;
        try {
            this.windowManager.addView(this.floatingView, layoutParams);
            Log.d(TAG, "Floating window created");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error creating floating window: " + e.getMessage());
        }
    }

    public void updateFeedbackText(String str) {
        if (str == null || str.isEmpty()) {
            return;
        }
        String strResolveDisplayText = resolveDisplayText(str);
        if (strResolveDisplayText == null || strResolveDisplayText.isEmpty()) {
            ScreenCaptureService.blog("FLOAT: display text empty, skip");
            return;
        }
        if (this.floatingView == null || this.feedbackText == null) {
            createFloatingWindow();
        }
        if (strResolveDisplayText.length() > MAX_DISPLAY_CHARS) {
            strResolveDisplayText = strResolveDisplayText.substring(0, MAX_DISPLAY_CHARS) + "...";
        }
        TextView textView = this.feedbackText;
        if (textView != null) {
            textView.setText(strResolveDisplayText);
            ScreenCaptureService.blog("FLOAT: displayed");
        }
        View view = this.floatingView;
        if (view != null && view.getVisibility() != 0) {
            this.floatingView.setVisibility(0);
        }
        this.handler.removeCallbacks(this.clearTextRunnable);
        this.handler.postDelayed(this.clearTextRunnable, CLEAR_TEXT_DELAY_MS);
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

    @Override // android.app.Service
    public void onDestroy() {
        WindowManager windowManager;
        super.onDestroy();
        try {
            this.handler.removeCallbacks(this.clearTextRunnable);
            unregisterReceiver(this.updateReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        View view = this.floatingView;
        if (view == null || (windowManager = this.windowManager) == null) {
            return;
        }
        windowManager.removeView(view);
    }
}
