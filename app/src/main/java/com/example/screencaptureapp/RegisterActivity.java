package com.example.screencaptureapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * 注册界面 —— 邮箱 + 验证码 + 密码
 * 流程：输入邮箱 → 发送验证码 → 输入验证码+密码 → 注册成功 → 自动登录
 */
public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etCode, etPassword, etConfirmPassword;
    private Button btnSendCode, btnRegisterSubmit, btnBackLogin;
    private TextView tvCodeHint;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int countdownSeconds = 0;
    private boolean codeSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.et_email);
        etCode = findViewById(R.id.et_code);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnRegisterSubmit = findViewById(R.id.btn_register_submit);
        btnBackLogin = findViewById(R.id.btn_back_login);
        tvCodeHint = findViewById(R.id.tv_code_hint);
    }

    private void setupListeners() {
        btnSendCode.setOnClickListener(v -> sendVerificationCode());
        btnRegisterSubmit.setOnClickListener(v -> performRegister());
        btnBackLogin.setOnClickListener(v -> finish());
    }

    /**
     * 发送邮箱验证码
     */
    private void sendVerificationCode() {
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            showError("请输入邮箱地址");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("请输入正确的邮箱格式");
            return;
        }

        btnSendCode.setEnabled(false);
        btnSendCode.setText("发送中...");

        new Thread(() -> {
            try {
                ApiService api = ApiService.getInstance(this);
                ApiService.AuthResponse response = api.sendCode(email);

                runOnUiThread(() -> {
                    if (response.isSuccess()) {
                        codeSent = true;
                        Toast.makeText(RegisterActivity.this,
                                "验证码已发送至您的邮箱", Toast.LENGTH_SHORT).show();
                        if (tvCodeHint != null) {
                            tvCodeHint.setText("验证码已发送至 " + email + "，5分钟内有效");
                            tvCodeHint.setTextColor(getColor(R.color.gto_success));
                        }
                        startCountdown(60);
                    } else {
                        codeSent = false;
                        btnSendCode.setEnabled(true);
                        btnSendCode.setText("发送验证码");
                        showError(response.message.isEmpty() ? "发送失败，请重试" : response.message);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    codeSent = false;
                    btnSendCode.setEnabled(true);
                    btnSendCode.setText("发送验证码");
                    showError("网络连接失败: " + e.getMessage());
                });
            }
        }).start();
    }

    private void startCountdown(int seconds) {
        countdownSeconds = seconds;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (countdownSeconds > 0) {
                    btnSendCode.setText(String.format(Locale.getDefault(), "%ds后重发", countdownSeconds));
                    btnSendCode.setEnabled(false);
                    countdownSeconds--;
                    handler.postDelayed(this, 1000);
                } else {
                    btnSendCode.setText("发送验证码");
                    btnSendCode.setEnabled(true);
                }
            }
        });
    }

    /**
     * 注册
     */
    private void performRegister() {
        String email = etEmail.getText().toString().trim();
        String code = etCode.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // 校验邮箱
        if (TextUtils.isEmpty(email)) {
            showError("请输入邮箱地址");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("请输入正确的邮箱格式");
            return;
        }

        // 校验验证码
        if (!codeSent) {
            showError("请先获取验证码");
            return;
        }
        if (TextUtils.isEmpty(code)) {
            showError("请输入邮箱验证码");
            return;
        }
        if (code.length() != 6 || !code.matches("\\d+")) {
            showError("验证码为6位数字");
            return;
        }

        // 校验密码
        if (TextUtils.isEmpty(password)) {
            showError("请输入密码");
            return;
        }
        if (password.length() < 6 || password.length() > 32) {
            showError("密码长度需要 6-32 位");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("两次输入的密码不一致");
            return;
        }

        setButtonsEnabled(false);
        btnRegisterSubmit.setText("注册中...");

        new Thread(() -> {
            try {
                ApiService api = ApiService.getInstance(this);
                // 第一步：注册
                ApiService.AuthResponse registerResp = api.register(email, code, password);

                if (!registerResp.isSuccess()) {
                    runOnUiThread(() -> {
                        setButtonsEnabled(true);
                        btnRegisterSubmit.setText(R.string.btn_register_submit);
                        showError(registerResp.message.isEmpty() ? "注册失败，请重试" : registerResp.message);
                    });
                    return;
                }

                // 第二步：注册成功后自动登录
                ApiService.AuthResponse loginResp = api.login(email, password);

                runOnUiThread(() -> {
                    setButtonsEnabled(true);
                    btnRegisterSubmit.setText(R.string.btn_register_submit);

                    if (loginResp.isSuccess()) {
                        String token = loginResp.data != null && loginResp.data.has("token")
                                ? loginResp.data.get("token").getAsString() : "";
                        String accountId = "";
                        String accountEmail = email;

                        if (loginResp.data != null && loginResp.data.has("account")) {
                            JsonObject account = loginResp.data.get("account").getAsJsonObject();
                            accountId = account.has("id")
                                    ? String.valueOf(account.get("id").getAsInt()) : "";
                            accountEmail = account.has("email")
                                    ? account.get("email").getAsString() : email;
                        }

                        UserSessionManager session = UserSessionManager.getInstance(RegisterActivity.this);
                        session.saveLoginInfo(token, accountEmail, accountId);

                        // 记录 token 过期时间
                        if (loginResp.data != null && loginResp.data.has("expires_at")) {
                            session.setTokenExpiresAt(parseIsoTime(loginResp.data.get("expires_at").getAsString()));
                        }

                        Toast.makeText(RegisterActivity.this,
                                "注册成功，已自动登录", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        // 注册成功但登录失败（异常情况）
                        showError("注册成功但自动登录失败，请手动登录");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setButtonsEnabled(true);
                    btnRegisterSubmit.setText(R.string.btn_register_submit);
                    showError("网络连接失败: " + e.getMessage());
                });
            }
        }).start();
    }

    private void setButtonsEnabled(boolean enabled) {
        btnRegisterSubmit.setEnabled(enabled);
        btnBackLogin.setEnabled(enabled);
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(DialogTextUtils.redBold("提示"))
                .setMessage(DialogTextUtils.redBold(message))
                .setPositiveButton("确定", null)
                .show();
    }

    /** 解析 ISO 8601 时间字符串为毫秒时间戳 */
    private static long parseIsoTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return 0;
        try {
            String cleaned = isoTime.replace("Z", "+00:00");
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.getDefault());
            return sdf.parse(cleaned).getTime();
        } catch (Exception e) {
            try {
                String cleaned = isoTime.replace("Z", "+00:00");
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.getDefault());
                return sdf.parse(cleaned).getTime();
            } catch (Exception e2) {
                return 0;
            }
        }
    }
}
