package com.example.screencaptureapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;

import com.example.screencaptureapp.utils.PermissionHelper;

import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 平台控制台主界面 —— 整合录屏鉴权逻辑
 * 使用 account_id（UUID）调用激活/额度查询接口
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;
    private static final int REQUEST_OVERLAY_PERMISSION = 1002;
    private static final int REQUEST_PERMISSIONS = 1003;
    private static final int REQUEST_MEDIA_PROJECTION_PERMISSION = 1004;

    // ---- UI ----
    private TextView tvQuota, tvExpireAt, tvAccountEmail, tvDeviceStatus, tvDeviceCheck;
    private EditText etLicenseCode;
    private Button btnActivateLicense;
    private ImageButton btnRefreshQuota;
    private Button btnEnterAAPoker, btnStopRecording, btnStartRecording;
    private Button btnWebsite, btnAbout, btnLogout, btnReactivate;
    private LinearLayout layoutRecordingStatus;
    private LinearLayout layoutLicenseActivation;
    private ImageView ivAapokerIcon;

    // ---- 录屏 ----
    private MediaProjectionManager mediaProjectionManager;
    private ScreenCaptureService screenCaptureService;
    private boolean isServiceBound = false;
    private boolean isEnterAAPokerMode = false;  // true=需自动拉起AAPoker, false=仅录屏

    private UserSessionManager session;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScreenCaptureService.LocalBinder binder = (ScreenCaptureService.LocalBinder) service;
            screenCaptureService = binder.getService();
            isServiceBound = true;
            ScreenCaptureService.blog("[Main] Service bound");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = UserSessionManager.getInstance(this);
        if (!session.isLoggedIn()) {
            redirectToLogin();
            return;
        }
        setContentView(R.layout.activity_main);
        initViews();
        checkPermissions();
        loadSessionData();
        setupListeners();

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    // ==================== 初始化 ====================

    private void initViews() {
        tvQuota = findViewById(R.id.tv_quota);
        tvExpireAt = findViewById(R.id.tv_expire_at);
        tvAccountEmail = findViewById(R.id.tv_account_phone);
        tvDeviceStatus = findViewById(R.id.tv_device_status);
        tvDeviceCheck = findViewById(R.id.tv_device_check);
        etLicenseCode = findViewById(R.id.et_license_code);
        btnRefreshQuota = findViewById(R.id.btn_refresh_quota);
        btnActivateLicense = findViewById(R.id.btn_activate_license);
        btnEnterAAPoker = findViewById(R.id.btn_enter_aapoker);
        btnStopRecording = findViewById(R.id.btn_stop_recording);
        btnStartRecording = findViewById(R.id.btn_start_recording);
        btnWebsite = findViewById(R.id.btn_website);
        btnAbout = findViewById(R.id.btn_about);
        btnLogout = findViewById(R.id.btn_logout);
        btnReactivate = findViewById(R.id.btn_reactivate);
        layoutRecordingStatus = findViewById(R.id.layout_recording_status);
        layoutLicenseActivation = findViewById(R.id.layout_license_activation);
        ivAapokerIcon = findViewById(R.id.iv_aapoker_icon);

        // 尝试加载 AAPoker 实际图标
        try {
            ivAapokerIcon.setImageDrawable(
                    getPackageManager().getApplicationIcon("com.ty.aapk"));
        } catch (Exception e) {
            ivAapokerIcon.setImageResource(R.mipmap.ic_launcher);
        }
    }

    private void loadSessionData() {
        // 显示邮箱
        String email = session.getEmail();
        tvAccountEmail.setText(email.isEmpty() ? "---" : email);

        updateDeviceStatus();
        updateQuotaDisplay(session.getEffectiveQuota());
        updateExpireDisplay(session.getExpireAt());

        /*String savedCode = session.getLicenseCode();
        if (!TextUtils.isEmpty(savedCode)) {
            etLicenseCode.setText(savedCode);
        }*/

        updateRecordingUI();
        updateLicenseActivationUI();
    }

    private void updateDeviceStatus() {
        if (session.isDeviceBound()) {
            tvDeviceStatus.setText(R.string.device_bound);
            tvDeviceCheck.setVisibility(View.VISIBLE);
        } else {
            tvDeviceStatus.setText(R.string.device_not_bound);
            tvDeviceCheck.setVisibility(View.GONE);
        }
    }

    private void updateQuotaDisplay(long quota) {
        if (quota > 0) {
            tvQuota.setText(String.valueOf(quota));
            tvQuota.setTextColor(getColor(R.color.gto_accent));
            tvQuota.setVisibility(View.VISIBLE);
            tvExpireAt.setVisibility(View.GONE);
        } else {
            tvQuota.setVisibility(View.GONE);
        }
    }

    private void updateExpireDisplay(long expireAt) {
        long quota = session.getEffectiveQuota();
        if (quota > 0) {
            tvExpireAt.setVisibility(View.GONE);
            return;
        }
        if (expireAt > 0) {
            tvExpireAt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tvExpireAt.setTypeface(tvExpireAt.getTypeface(), Typeface.BOLD);
            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(expireAt * 1000));
            if (session.isExpired()) {
                tvExpireAt.setText("已过期: " + dateStr);
                tvExpireAt.setTextColor(getColor(R.color.gto_error));
            } else {
                tvExpireAt.setText("有效期至: " + dateStr);
                tvExpireAt.setTextColor(getColor(R.color.gto_warning));
            }
            tvExpireAt.setVisibility(View.VISIBLE);
        } else {
            tvExpireAt.setVisibility(View.GONE);
        }
    }

    // ==================== 权限 ====================

    private void checkPermissions() {
        List<String> permissionList = new ArrayList<>();
        permissionList.add(Manifest.permission.INTERNET);
        permissionList.add(Manifest.permission.ACCESS_NETWORK_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        String[] permissions = permissionList.toArray(new String[0]);
        if (!PermissionHelper.hasPermissions(this, permissions)) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            }
        }
    }

    // ==================== 按钮监听 ====================

    private void setupListeners() {
        btnRefreshQuota.setOnClickListener(v -> refreshQuota());
        btnActivateLicense.setOnClickListener(v -> activateLicense());
        btnEnterAAPoker.setOnClickListener(v -> enterAAPoker());
        btnStartRecording.setOnClickListener(v -> startRecordingOnly());
        btnStopRecording.setOnClickListener(v -> stopRecording());
        btnReactivate.setOnClickListener(v -> {
            layoutLicenseActivation.setVisibility(View.VISIBLE);
            btnReactivate.setVisibility(View.GONE);
        });

        btnWebsite.setOnClickListener(v ->
                Toast.makeText(this, "访问官网功能开发中...", Toast.LENGTH_SHORT).show());

        btnAbout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("关于 桃园助手")
                .setMessage("桃园助手 " + getString(R.string.app_version) + "\n\n专业扑克决策辅助平台\n桃园团队出品\n\n© 2026 桃园助手")
                .setPositiveButton("确定", null)
                .show());

        btnLogout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出当前账户吗？")
                .setPositiveButton("确定", (dialog, which) -> doLogout())
                .setNegativeButton("取消", null)
                .show());
    }

    // ==================== 退出登录 ====================

    private void doLogout() {
        String token = session.getToken();
        if (token != null && !token.isEmpty()) {
            new Thread(() -> {
                try {
                    ApiService api = ApiService.getInstance(this);
                    api.logout(token);  // 不关心结果，尽力通知服务端
                } catch (Exception ignored) {
                }
            }).start();
        }
        session.logout();
        redirectToLogin();
    }

    // ==================== 录屏状态 ====================

    private void updateRecordingUI() {
        boolean isRecording = isServiceBound && screenCaptureService != null
                && screenCaptureService.isRecording();
        layoutRecordingStatus.setVisibility(isRecording ? View.VISIBLE : View.GONE);
    }

    private void updateLicenseActivationUI() {
        boolean activated = session.isLicenseActivated();
        layoutLicenseActivation.setVisibility(activated ? View.GONE : View.VISIBLE);
        btnReactivate.setVisibility(activated ? View.VISIBLE : View.GONE);
    }

    // ==================== 刷新额度 ====================

    private void refreshQuota() {
        btnRefreshQuota.setEnabled(false);
        btnRefreshQuota.setAlpha(0.3f);

        new Thread(() -> {
            try {
                String accountId = session.getAccountId();
                ApiService api = ApiService.getInstance(MainActivity.this);
                ApiService.BizResponse response = api.getQuota(session.getEmail(), session.getToken());

                runOnUiThread(() -> {
                    btnRefreshQuota.setEnabled(true);
                    btnRefreshQuota.setAlpha(1.0f);

                    if (api.isBizSuccess(response) && response.data != null) {
                        JsonObject d = response.data;
                        long quota = d.has("amount") ? d.get("amount").getAsLong() : 0;
                        long expireAt = d.has("expire_at") ? d.get("expire_at").getAsLong() : 0;

                        session.setQuota(quota);
                        session.setExpireAt(expireAt);

                        boolean isActive = d.has("is_active") && d.get("is_active").getAsBoolean();
                        session.setLicenseActivated(isActive);
                        session.setDeviceBound(isActive);
                        updateDeviceStatus();
                        updateLicenseActivationUI();

                        updateQuotaDisplay(session.getEffectiveQuota());
                        updateExpireDisplay(expireAt);
                        Toast.makeText(MainActivity.this, "额度已刷新", Toast.LENGTH_SHORT).show();
                    } else {
                        showError(response.statusMessage.isEmpty() ? "获取额度失败" : response.statusMessage);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnRefreshQuota.setEnabled(true);
                    btnRefreshQuota.setAlpha(1.0f);
                    showError("网络连接失败: " + e.getMessage());
                });
            }
        }).start();
    }

    // ==================== 激活授权码 ====================

    private void activateLicense() {
        String licenseCode = etLicenseCode.getText().toString().trim();

        if (TextUtils.isEmpty(licenseCode)) {
            showError("请输入授权码");
            return;
        }

        btnActivateLicense.setEnabled(false);
        btnActivateLicense.setText("激活中...");

        new Thread(() -> {
            try {
                String accountId = session.getAccountId();
                ApiService api = ApiService.getInstance(MainActivity.this);
                ApiService.BizResponse response = api.activateLicense(session.getEmail(), licenseCode, session.getToken());

                runOnUiThread(() -> {
                    btnActivateLicense.setEnabled(true);
                    btnActivateLicense.setText(R.string.btn_activate);

                    if (api.isBizSuccess(response)) {
                        session.setLicenseActivated(true);
                        session.setDeviceBound(true);
                        updateDeviceStatus();

                        Toast.makeText(MainActivity.this,
                                "授权码激活成功", Toast.LENGTH_SHORT).show();

                        // 激活成功后自动刷新额度并隐藏激活区域
                        //updateLicenseActivationUI();
                        refreshQuota();
                    } else {
                        showError(response.statusMessage.isEmpty()
                                ? "授权码激活失败" : response.statusMessage);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnActivateLicense.setEnabled(true);
                    btnActivateLicense.setText(R.string.btn_activate);
                    showError("网络连接失败: " + e.getMessage());
                });
            }
        }).start();
    }

    // ==================== 进入 AAPoker（含录屏鉴权） ====================

    /**
     * 点击「进入」时：
     * 1. 本地快速校验（授权码+额度缓存）
     * 2. 通过后 → HTTP 实时查额度（防止本地缓存过期）
     * 3. 实时校验通过 → 开始录屏鉴权 + 拉起 AAPoker
     */
    private void enterAAPoker() {
        // 本地快速校验
        if (!session.canEnterScreenCapture()) {
            showError(session.getCannotEnterReason());
            return;
        }

        /*if (TextUtils.isEmpty(session.getLicenseCode())) {
            showError("请先激活授权码");
            return;
        }*/

        if (isServiceBound && screenCaptureService != null && screenCaptureService.isRecording()) {
            showError("录屏已在运行中，请先停止当前录制");
            return;
        }

        isEnterAAPokerMode = true;
        doRealtimeValidationAndStartCapture();
    }

    /**
     * 仅开始录屏，不自动拉起 AAPoker——用户手动切 App
     */
    private void startRecordingOnly() {
        if (!session.canEnterScreenCapture()) {
            showError(session.getCannotEnterReason());
            return;
        }

        /*if (TextUtils.isEmpty(session.getLicenseCode())) {
            showError("请先激活授权码");
            return;
        }*/

        if (isServiceBound && screenCaptureService != null && screenCaptureService.isRecording()) {
            showError("录屏已在运行中，请先停止当前录制");
            return;
        }

        isEnterAAPokerMode = false;
        doRealtimeValidationAndStartCapture();
    }

    private void doRealtimeValidationAndStartCapture() {
        // API 34+ 权限预检（前置，免得实时校验通过后才弹，用户体验差）
        if (Build.VERSION.SDK_INT >= 34) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION},
                        REQUEST_MEDIA_PROJECTION_PERMISSION);
                return;
            }
        }

        // ---- 实时校验：通过 HTTP 查询最新额度，防止本地缓存过期 ----
        ScreenCaptureService.blog("[Main] Validation: start mode=" + (isEnterAAPokerMode ? "enter" : "recOnly"));
        btnEnterAAPoker.setEnabled(false);
        btnStartRecording.setEnabled(false);
        if (isEnterAAPokerMode) {
            btnEnterAAPoker.setText("校验中...");
        } else {
            btnStartRecording.setText("校验中...");
        }

        new Thread(() -> {
            try {
                String token = session.getToken();
                String accountId = session.getAccountId();
                ApiService api = ApiService.getInstance(MainActivity.this);

                ScreenCaptureService.blog("[Main] Validation: verifying token");
                ApiService.AuthResponse tokenResp = api.verifyToken(token);
                if (!tokenResp.isSuccess() || tokenResp.data == null
                        || !tokenResp.data.has("valid")
                        || !tokenResp.data.get("valid").getAsBoolean()) {
                    ScreenCaptureService.blog("[Main] Validation: token invalid");
                    runOnUiThread(() -> {
                        btnEnterAAPoker.setEnabled(true);
                        btnEnterAAPoker.setText(R.string.btn_enter);
                        btnStartRecording.setEnabled(true);
                        btnStartRecording.setText(R.string.btn_start_recording);
                        session.logout();
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("登录已过期")
                                .setMessage("您的登录已过期，请重新登录")
                                .setPositiveButton("确定", (d, w) -> redirectToLogin())
                                .setCancelable(false)
                                .show();
                    });
                    return;    // 终止，不再执行后续的额度查询和录屏鉴权
                }

                ScreenCaptureService.blog("[Main] Validation: token OK, querying quota");
                ApiService.BizResponse response = api.getQuota(session.getEmail(), session.getToken());
                runOnUiThread(() -> {
                    btnEnterAAPoker.setEnabled(true);
                    btnEnterAAPoker.setText(R.string.btn_enter);
                    btnStartRecording.setEnabled(true);
                    btnStartRecording.setText(R.string.btn_start_recording);

                    if (!api.isBizSuccess(response)) {
                        showError(response.statusMessage.isEmpty()
                                ? "校验授权状态失败" : response.statusMessage);
                        return;
                    }

                    // 用实时数据更新本地缓存
                    JsonObject d = response.data;
                    long quota = d.has("amount") ? d.get("amount").getAsLong() : 0;
                    long expireAt = d.has("expire_at") ? d.get("expire_at").getAsLong() : 0;

                    session.setQuota(quota);
                    session.setExpireAt(expireAt);
                    updateQuotaDisplay(session.getEffectiveQuota());
                    updateExpireDisplay(expireAt);

                    boolean isActive = d.has("is_active") && d.get("is_active").getAsBoolean();
                    session.setLicenseActivated(isActive);
                    session.setDeviceBound(isActive);
                    updateDeviceStatus();
                    updateLicenseActivationUI();

                    // 实时校验是否仍然满足条件
                    if (!session.canEnterScreenCapture()) {
                        showError(session.getCannotEnterReason());
                        return;
                    }

                    // 全部通过 → 开始录屏鉴权
                    ScreenCaptureService.blog("[Main] Validation: PASS, starting screen capture auth");
                    startScreenCaptureAuth();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnEnterAAPoker.setEnabled(true);
                    btnEnterAAPoker.setText(R.string.btn_enter);
                    btnStartRecording.setEnabled(true);
                    btnStartRecording.setText(R.string.btn_start_recording);
                    showError("网络连接失败: " + e.getMessage());
                });
            }
        }).start();
    }

    private void startScreenCaptureAuth() {
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(new Intent(this, ScreenCaptureService.class),
                serviceConnection, BIND_AUTO_CREATE);

        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE);
    }

    private void stopRecording() {
        if (isServiceBound && screenCaptureService != null) {
            ScreenCaptureService.blog("[Main] stopRecording from user");
            screenCaptureService.stopRecording();
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        stopService(new Intent(this, ScreenCaptureService.class));
        stopService(new Intent(this, FloatingWindowService.class));
        updateRecordingUI();
        Toast.makeText(this, "录制已停止", Toast.LENGTH_SHORT).show();
    }

    private void launchAAPoker() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.ty.aapk");
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
            Toast.makeText(this, "AAPoker 已启动", Toast.LENGTH_SHORT).show();
        } else {
            try {
                Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.ty.aapk"));
                startActivity(marketIntent);
            } catch (Exception e) {
                Toast.makeText(this, "未找到 AAPoker 应用，请先安装", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== Activity 结果回调 ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && isServiceBound) {
                ScreenCaptureService.blog("[Main] onAR OK: enterMode=" + isEnterAAPokerMode);
                Intent intent = new Intent(this, FloatingWindowService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                screenCaptureService.startRecording(resultCode, data);
                updateRecordingUI();
                Toast.makeText(this, "录屏已开始", Toast.LENGTH_SHORT).show();
                if (isEnterAAPokerMode) {
                    launchAAPoker();
                }
            } else {
                ScreenCaptureService.blog("[Main] onAR FAIL: rc=" + resultCode + " bound=" + isServiceBound);
                Toast.makeText(this, "屏幕录制权限被拒绝", Toast.LENGTH_SHORT).show();
                stopRecording();
            }
        } else if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "悬浮窗权限已获取", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                Toast.makeText(this, "所有权限已获取", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_MEDIA_PROJECTION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予 → 重新进入 enterAAPoker（会跳过权限检查，走实时校验）
                //enterAAPoker();
                doRealtimeValidationAndStartCapture();
            } else {
                Toast.makeText(this, "需要媒体投影权限才能继续", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== 工具 ====================

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("提示").setMessage(message)
                .setPositiveButton("确定", null).show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRecordingUI();
        if (isServiceBound && screenCaptureService != null) {
            ScreenCaptureService.blog("[Main] onResume bound=" + isServiceBound + " rec=" + screenCaptureService.isRecording());
        }

        // 检查登录 token 是否过期
        if (session.isTokenExpired()) {
            ScreenCaptureService.blog("[Main] onResume: token expired, logging out");
            session.logout();
            if(isServiceBound && screenCaptureService != null && screenCaptureService.isRecording())
            {
                ScreenCaptureService.blog("[Main] onResume: token expired, stopping rec");
                stopRecording();
            }
            new AlertDialog.Builder(this)
                    .setTitle("登录已过期")
                    .setMessage("您的登录已过期，请重新登录")
                    .setPositiveButton("确定", (d, w) -> redirectToLogin())
                    .setCancelable(false)
                    .show();
            return;
        }

        // 如果正在录制，实时校验授权状态（防止录制期间授权过期或额度耗尽）
        if (isServiceBound && screenCaptureService != null && screenCaptureService.isRecording()) {
            verifyAuthorizationWhileRecording();
        }
    }

    /**
     * 录制期间校验授权状态：通过 HTTP 实时查询额度
     * 如果授权已过期或额度不足，弹出提示并主动停止录制
     */
    private void verifyAuthorizationWhileRecording() {
        ScreenCaptureService.blog("[Main] verifyAuth: start rec check");
        new Thread(() -> {
            try {
                String accountId = session.getAccountId();
                ApiService api = ApiService.getInstance(MainActivity.this);
                ApiService.BizResponse response = api.getQuota(session.getEmail(), session.getToken());

                runOnUiThread(() -> {
                    if (!api.isBizSuccess(response)) {
                        ScreenCaptureService.blog("[Main] verifyAuth: API fail, skipping");
                        // 接口调用失败，不中断录制（避免网络波动误杀）
                        return;
                    }

                    JsonObject d = response.data;
                    long quota = d.has("amount") ? d.get("amount").getAsLong() : 0;
                    long expireAt = d.has("expire_at") ? d.get("expire_at").getAsLong() : 0;

                    // 更新本地缓存
                    session.setQuota(quota);
                    session.setExpireAt(expireAt);
                    updateQuotaDisplay(session.getEffectiveQuota());
                    updateExpireDisplay(expireAt);

                    boolean isActive = d.has("is_active") && d.get("is_active").getAsBoolean();
                    session.setLicenseActivated(isActive);
                    session.setDeviceBound(isActive);
                    updateDeviceStatus();
                    updateLicenseActivationUI();

                    // 检查授权是否仍然有效
                    if (!session.canEnterScreenCapture()) {
                        // 授权失效 → 弹窗提示并停止录制
                        String reason = session.getCannotEnterReason();
                        ScreenCaptureService.blog("[Main] verifyAuth: auth lost, stopping rec: " + reason);
                        stopRecording();
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("录制已停止")
                                .setMessage("您的授权已失效：" + reason)
                                .setPositiveButton("确定", null)
                                .show();
                    } else {
                        ScreenCaptureService.blog("[Main] verifyAuth: rec OK, q=" + quota + " active=" + isActive);
                    }
                });
            } catch (Exception e) {
                // 网络异常不中断录制
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) unbindService(serviceConnection);
    }
}
