package com.example.screencaptureapp;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import android.content.BroadcastReceiver;

public class FloatingWindowService extends Service {

    public static final String ACTION_UPDATE_TEXT = "UPDATE_TEXT";
    public static final String EXTRA_TEXT = "text";
    public static final String ACTION_HIDE_WINDOW = "HIDE_WINDOW";
    public static final String ACTION_SHOW_WINDOW = "SHOW_WINDOW";

    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "floating_window_channel";
    private static final int NOTIFICATION_ID = 1002;

    private WindowManager windowManager;
    private View floatingView;
    private TextView feedbackText;
    private Handler handler;
    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            if (floatingView != null) {
                floatingView.setVisibility(View.INVISIBLE);
            }
        }
    };

    // 接收悬浮窗更新的广播
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_UPDATE_TEXT.equals(intent.getAction())) {
                String text = intent.getStringExtra(EXTRA_TEXT);
                if (text != null) {
                    updateFeedbackText(text);
                }
            }
            else if(ACTION_HIDE_WINDOW.equals(intent.getAction())) {
                if (floatingView != null ) {
                    floatingView.setVisibility(View.INVISIBLE);
                }
            }
            else if(ACTION_SHOW_WINDOW.equals(intent.getAction())) {
                if (floatingView != null) {
                    floatingView.setVisibility(View.VISIBLE);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(getMainLooper());
        createNotificationChannel();

        // 注册广播接收器
        //IntentFilter filter = new IntentFilter(ACTION_UPDATE_TEXT);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_UPDATE_TEXT);
        filter.addAction(ACTION_HIDE_WINDOW);
        filter.addAction(ACTION_SHOW_WINDOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        // 关键：立即启动前台服务
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (floatingView == null) {
            createFloatingWindow();
        }

        return START_STICKY;
    }

    private void createFloatingWindow() {
        LayoutInflater inflater = LayoutInflater.from(this);
        floatingView = inflater.inflate(R.layout.floating_window, null);

        feedbackText = floatingView.findViewById(R.id.tv_feedback);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        //params.gravity = Gravity.TOP | Gravity.END;
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.x = 20;
        params.y = 100;
        params.windowAnimations = 0;

        try {
            windowManager.addView(floatingView, params);
            Log.d(TAG, "Floating window created");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error creating floating window: " + e.getMessage());
        }
    }

    private void updateFeedbackText(final String text) {
        if(text == null || text.isEmpty()) return;

        // 从 JSON 中提取 result 字段
        final String displayText;
        try {
            com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(text);
            if (element.isJsonObject()) {
                com.google.gson.JsonObject json = element.getAsJsonObject();
                if (json.has("Result") && !json.get("Result").isJsonNull()) {
                    String result = json.get("Result").getAsString();
                    ScreenCaptureService.blog("FLOAT: json parsed, Result=" + result);
                    if (!result.isEmpty()) {
                        displayText = text;
                    } else {
                        ScreenCaptureService.blog("FLOAT: Result empty, skip");
                        return;
                    }
                } else {
                    ScreenCaptureService.blog("FLOAT: no Result field in json");
                    return;
                }
            } else {
                ScreenCaptureService.blog("FLOAT: not a JSON object");
                return;
            }
        } catch (Exception e) {
            ScreenCaptureService.blog("FLOAT: json parse err " + e.getMessage());
            return;
        }

        handler.post(() -> {
            if(feedbackText != null) {
                feedbackText.setText(displayText);
                ScreenCaptureService.blog("FLOAT: displayed");
            }

            handler.removeCallbacks(hideRunnable);

            if(floatingView != null) {
                floatingView.setVisibility(View.VISIBLE);
            }
            // 5s后自动隐藏
            handler.postDelayed(hideRunnable, 5000);
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "悬浮窗服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("显示屏幕录制反馈");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕录制反馈")
                .setContentText("显示录制状态反馈")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            handler.removeCallbacks(hideRunnable);
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
