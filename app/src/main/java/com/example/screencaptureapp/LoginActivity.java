package com.example.screencaptureapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;

/**
 * 登录界面 —— 邮箱 + 密码登录
 * 启动时先检查本地 token，存在则校验 token 有效性
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        UserSessionManager session = UserSessionManager.getInstance(this);

        // 有本地 token → 先校验有效性
        if (session.hasToken()) {
            // 先加载布局，按钮显示校验状态
            setContentView(R.layout.activity_login);
            initViews();
            setButtonsEnabled(false);
            btnLogin.setText("验证登录状态...");
            verifyStoredToken(session);
        } else {
            setContentView(R.layout.activity_login);
            showLoginUI();
        }
    }

    /**
     * 校验本地存储的 token 是否仍然有效
     */
    private void verifyStoredToken(UserSessionManager session) {
        new Thread(() -> {
            try {
                String token = session.getToken();
                ApiService api = ApiService.getInstance(this);
                ApiService.AuthResponse response = api.verifyToken(token);

                runOnUiThread(() -> {
                    if (response.isSuccess() && response.data != null
                            && response.data.has("valid")
                            && response.data.get("valid").getAsBoolean()) {
                        // Token 有效 → 刷新账户信息，直接进入主界面
                        if (response.data.has("account")) {
                            JsonObject account = response.data.get("account").getAsJsonObject();
                            String email = account.has("email") ? account.get("email").getAsString() : session.getEmail();
                            String id = account.has("id") ? account.get("id").getAsString() : session.getAccountId();
                            session.saveLoginInfo(token, email, id);
                        }
                        // 记录 token 过期时间
                        if (response.data.has("expires_at")) {
                            session.setTokenExpiresAt(parseIsoTime(response.data.get("expires_at").getAsString()));
                        }
                        //Toast.makeText(LoginActivity.this, "自动登录成功", Toast.LENGTH_SHORT).show();
                        startMainActivity();
                        finish();
                    } else {
                        // Token 无效 → 清除并显示登录界面
                        session.logout();
                        showLoginUI();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    // 网络不通 → 直接显示登录界面（不清除 token，下次可重试）
                    showLoginUI();
                });
            }
        }).start();
    }

    private void showLoginUI() {
        initViews();
        setupListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);

        if (btnLogin != null) {
            btnLogin.setEnabled(true);
            btnLogin.setText(R.string.btn_login);
        }
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> performLogin());

        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void performLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // 邮箱格式校验
        if (TextUtils.isEmpty(email)) {
            showError("请输入邮箱地址");
            return;
        }
        if (!isValidEmail(email)) {
            showError("请输入正确的邮箱格式");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showError("请输入密码");
            return;
        }
        if (password.length() < 6 || password.length() > 32) {
            showError("密码长度需要 6-32 位");
            return;
        }

        setButtonsEnabled(false);
        btnLogin.setText("登录中...");

        new Thread(() -> {
            try {
                ApiService api = ApiService.getInstance(this);
                ApiService.AuthResponse response = api.login(email, password);

                runOnUiThread(() -> {
                    setButtonsEnabled(true);
                    btnLogin.setText(R.string.btn_login);

                    if (response.isSuccess()) {
                        // 解析 token 和账户信息
                        String token = response.data != null && response.data.has("token")
                                ? response.data.get("token").getAsString() : "";
                        String accountId = "";
                        String accountEmail = email;

                        if (response.data != null && response.data.has("account")) {
                            JsonObject account = response.data.get("account").getAsJsonObject();
                            accountId = account.has("id")
                                    ? String.valueOf(account.get("id").getAsInt()) : "";
                            accountEmail = account.has("email")
                                    ? account.get("email").getAsString() : email;
                        }

                        UserSessionManager session = UserSessionManager.getInstance(LoginActivity.this);
                        session.saveLoginInfo(token, accountEmail, accountId);

                        // 记录 token 过期时间
                        if (response.data != null && response.data.has("expires_at")) {
                            session.setTokenExpiresAt(parseIsoTime(response.data.get("expires_at").getAsString()));
                        }

                        Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                        startMainActivity();
                        finish();
                    } else {
                        showError(response.message.isEmpty() ? "邮箱或密码错误" : response.message);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setButtonsEnabled(true);
                    btnLogin.setText(R.string.btn_login);
                    showError("网络连接失败: " + e.getMessage());
                });
            }
        }).start();
    }

    private void setButtonsEnabled(boolean enabled) {
        if (btnLogin != null) btnLogin.setEnabled(enabled);
        if (btnRegister != null) btnRegister.setEnabled(enabled);
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(DialogTextUtils.redBold("提示"))
                .setMessage(DialogTextUtils.redBold(message))
                .setPositiveButton("确定", null)
                .show();
    }

    private void startMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /** 简单邮箱格式校验 */
    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    /** 解析 ISO 8601 时间字符串为毫秒时间戳 */
    private static long parseIsoTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return 0;
        try {
            // 处理带 "Z" 结尾的 ISO 格式: "2026-07-29T00:00:00.000Z"
            String cleaned = isoTime.replace("Z", "+00:00");
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.getDefault());
            return sdf.parse(cleaned).getTime();
        } catch (Exception e) {
            // 尝试不带毫秒的格式
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
