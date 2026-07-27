package com.example.screencaptureapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 用户会话管理器
 * 管理登录状态、JWT token、用户邮箱、account_id、授权状态的持久化
 */
public class UserSessionManager {
    private static final String PREFS_NAME = "gto_user_session";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ACCOUNT_ID = "account_id";
    private static final String KEY_LICENSE_ACTIVATED = "license_activated";
    //private static final String KEY_LICENSE_CODE = "license_code";
    private static final String KEY_QUOTA = "quota";
    private static final String KEY_EXPIRE_AT = "expire_at";
    private static final String KEY_TOKEN_EXPIRES_AT = "token_expires_at";
    private static final String KEY_DEVICE_BOUND = "device_bound";

    private static UserSessionManager instance;
    private final SharedPreferences prefs;

    private UserSessionManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized UserSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserSessionManager(context);
        }
        return instance;
    }

    // ==================== 登录信息 ====================

    public void saveLoginInfo(String token, String email, String accountId) {
        saveLoginInfo(token, email, accountId, 0);
    }

    public void saveLoginInfo(String token, String email, String accountId, long tokenExpiresAt) {
        prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_TOKEN, token)
                .putString(KEY_EMAIL, email)
                .putString(KEY_ACCOUNT_ID, accountId)
                .putLong(KEY_TOKEN_EXPIRES_AT, tokenExpiresAt)
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public boolean hasToken() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    /** 返回 account_id（用于激活/额度查询的 uuid 参数） */
    public String getAccountId() {
        return prefs.getString(KEY_ACCOUNT_ID, "");
    }

    /**
     * 检查 JWT token 是否已过期（基于服务端返回的 expires_at）
     * tokenExpiresAt == 0 表示未记录过期时间，视为未过期
     */
    public boolean isTokenExpired() {
        long tokenExpiresAt = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0);
        if (tokenExpiresAt <= 0) return false;
        return System.currentTimeMillis() > tokenExpiresAt;
    }

    // ==================== 授权码与额度 ====================

    public void setLicenseActivated(boolean activated) {
        prefs.edit()
                .putBoolean(KEY_LICENSE_ACTIVATED, activated)
                .apply();
    }

    public boolean isLicenseActivated() {
        return prefs.getBoolean(KEY_LICENSE_ACTIVATED, false);
    }

    /*public String getLicenseCode() {
        return prefs.getString(KEY_LICENSE_CODE, "");
    }*/

    public void setQuota(long quota) {
        prefs.edit().putLong(KEY_QUOTA, quota).apply();
    }

    public long getQuota() {
        return prefs.getLong(KEY_QUOTA, 0);
    }

    public void setExpireAt(long expireAt) {
        prefs.edit().putLong(KEY_EXPIRE_AT, expireAt).apply();
    }

    public long getExpireAt() {
        return prefs.getLong(KEY_EXPIRE_AT, 0);
    }

    /**
     * 额度是否已过期（expire_at 为 0 表示无过期限制/按次数计费）
     */
    public boolean isExpired() {
        long expireAt = getExpireAt();
        if (expireAt <= 0) return false;
        return System.currentTimeMillis() / 1000 > expireAt;
    }

    /**
     * 获取有效额度（过期返回0）
     */
    public long getEffectiveQuota() {
        if (isExpired()) return 0;
        return getQuota();
    }

    // ==================== 设备绑定状态 ====================

    public void setDeviceBound(boolean bound) {
        prefs.edit().putBoolean(KEY_DEVICE_BOUND, bound).apply();
    }

    public boolean isDeviceBound() {
        return prefs.getBoolean(KEY_DEVICE_BOUND, false);
    }

    /**
     * 单独更新 token 过期时间（verifyToken 返回时调用）
     */
    public void setTokenExpiresAt(long tokenExpiresAt) {
        prefs.edit().putLong(KEY_TOKEN_EXPIRES_AT, tokenExpiresAt).apply();
    }

    // ==================== 退出登录 ====================

    public void logout() {
        prefs.edit().clear().apply();
    }

    /**
     * 检查是否可以进入录屏（授权码已激活 且 有效额度 > 0）
     */
    public boolean canEnterScreenCapture() {
        return isLicenseActivated() && (getEffectiveQuota() > 0 || !isExpired());
    }

    /**
     * 获取不能进入录屏的原因
     */
    public String getCannotEnterReason() {
        if (!isLicenseActivated()) {
            return "请先激活授权码";
        }
        if (isExpired()) {
            return "授权已过期，请联系管理员续费";
        }
        if (getEffectiveQuota() <= 0) {
            return "剩余授权额度不足，请联系管理员充值";
        }
        return "未知原因";
    }
}
