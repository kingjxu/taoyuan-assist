package com.example.screencaptureapp;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * API 服务 - 处理所有 HTTP 通信
 *
 * 两组 API：
 * - Auth API（认证服务）：/api/auth/*   → 响应格式 {code, message, data}
 * - Business API（业务服务）：/lyxz/*   → 响应格式含 BaseResp {StatusCode, StatusMessage}
 */
public class ApiService {
    private static final String TAG = "ApiService";

    // ---- 两套后端地址 ----
    private static final String AUTH_BASE_URL = "http://114.132.228.98";
    private static final String BUSINESS_BASE_URL = "http://ddbaby.site";

    private static ApiService instance;
    private final OkHttpClient client;
    private final Gson gson;
    private Context appContext;

    private ApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.gson = new Gson();
    }

    public static synchronized ApiService getInstance(Context context) {
        if (instance == null) {
            instance = new ApiService();
        }
        if (context != null) {
            instance.appContext = context.getApplicationContext();
        }
        return instance;
    }

    // ==================== 通用响应结构 ====================

    /** Auth API 响应 */
    public static class AuthResponse {
        public int code;
        public String message;
        public JsonObject data;

        public boolean isSuccess() {
            return code == 0;
        }
    }

    /** Business API 响应（含 BaseResp） */
    public static class BizResponse {
        public int statusCode;       // BaseResp.StatusCode
        public String statusMessage; // BaseResp.StatusMessage
        public JsonObject data;      // 完整响应的 data 部分（不含 BaseResp）
    }

    // ==================== Auth API ====================

    /**
     * 发送邮箱验证码
     * POST /api/auth/send-code
     */
    public AuthResponse sendCode(String email) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        JsonObject result = post(AUTH_BASE_URL + "/api/auth/send-code", body, null);
        return parseAuthResponse(result);
    }

    /**
     * 注册（验证码 + 密码）
     * POST /api/auth/register
     */
    public AuthResponse register(String email, String code, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("code", code);
        body.addProperty("password", password);
        JsonObject result = post(AUTH_BASE_URL + "/api/auth/register", body, null);
        return parseAuthResponse(result);
    }

    /**
     * 登录（邮箱 + 密码）
     * POST /api/auth/login
     */
    public AuthResponse login(String email, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        JsonObject result = post(AUTH_BASE_URL + "/api/auth/login", body, null);
        return parseAuthResponse(result);
    }

    /**
     * 校验 Token
     * POST /api/auth/verify-token
     * 通过 Authorization Header 传 token
     */
    public AuthResponse verifyToken(String token) throws IOException {
        JsonObject result = post(AUTH_BASE_URL + "/api/auth/verify-token", null, token);
        return parseAuthResponse(result);
    }

    /**
     * 登出（撤销 Token）
     * POST /api/auth/logout
     */
    public AuthResponse logout(String token) throws IOException {
        JsonObject result = post(AUTH_BASE_URL + "/api/auth/logout", null, token);
        return parseAuthResponse(result);
    }

    // ==================== Business API（激活码 & 额度） ====================

    /**
     * 激活授权码
     * POST /lyxz/gto_active
     * Body: {"uuid":"xxx","active_code":"xxx"}
     * 响应包含 BaseResp
     */
    public BizResponse activateLicense(String accountId, String activeCode, String token) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", accountId);
        body.addProperty("active_code", activeCode);
        String url = BUSINESS_BASE_URL + "/lyxz/gto_active";
        JsonObject result = post(url, body, token);
        return parseBizResponse(result);
    }

    /**
     * 查询额度信息
     * POST /lyxz/gto_amount_info
     * Body: {"uuid":"xxx"}
     * 响应包含 BaseResp + IsActive + Amount + ExpireAt
     */
    public BizResponse getQuota(String accountId, String token) throws IOException {
        /*JsonObject body = new JsonObject();
        body.addProperty("uuid", accountId);
        String url = BUSINESS_BASE_URL + "/lyxz/gto_amount_info";
        JsonObject result = post(url, body, token);
        return parseBizResponse(result);*/
        String url = BUSINESS_BASE_URL + "/lyxz/gto_amount_info"
                + "?uuid=" + urlEncode(accountId);
        JsonObject result = get(url, token);
        return parseBizResponse(result);
    }

    // ==================== 底层 HTTP 方法 ====================

    private static String urlEncode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private JsonObject post(String url, JsonObject body, String token) throws IOException {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        Request.Builder builder = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "GTO/1.0");

        if (token != null && !token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }

        if (body != null) {
            builder.post(RequestBody.create(JSON, gson.toJson(body)))
                   .addHeader("Content-Type", "application/json");
        } else {
            // verify-token / logout 可能只传 token，body 可以为空
            builder.post(RequestBody.create(JSON, "{}"));
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.body() != null) {
                String responseBody = response.body().string();
                Log.d(TAG, "POST " + url + " -> " + responseBody);
                return JsonParser.parseString(responseBody).getAsJsonObject();
            }
            throw new IOException("Empty response body, code=" + response.code());
        }
    }

    private JsonObject get(String url, String token) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "GTO/1.0");
        if (token != null && !token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.body() != null) {
                String responseBody = response.body().string();
                Log.d(TAG, "GET " + url + " -> " + responseBody);
                return JsonParser.parseString(responseBody).getAsJsonObject();
            }
            throw new IOException("Empty response body, code=" + response.code());
        }
    }

    // ==================== 响应解析 ====================

    private AuthResponse parseAuthResponse(JsonObject json) {
        AuthResponse resp = new AuthResponse();
        resp.code = json.has("code") ? json.get("code").getAsInt() : -1;
        resp.message = json.has("message") ? json.get("message").getAsString() : "";
        resp.data = json.has("data") && !json.get("data").isJsonNull()
                ? json.get("data").getAsJsonObject() : null;
        return resp;
    }

    /**
     * 解析 Business API 响应
     * 响应结构可能有两种：
     * 1. 扁平结构: {"StatusMessage":"...","StatusCode":0,"IsActive":true,...} (BaseResp 字段和 data 字段同级)
     * 2. 嵌套结构: {"BaseResp":{"StatusMessage":"...","StatusCode":0},"data":{...}}
     */
    private BizResponse parseBizResponse(JsonObject json) {
        BizResponse resp = new BizResponse();

        // 先检查是否有嵌套的 BaseResp
        if (json.has("BaseResp")) {
            JsonObject baseResp = json.get("BaseResp").getAsJsonObject();
            resp.statusCode = baseResp.has("StatusCode") ? baseResp.get("StatusCode").getAsInt() : -1;
            resp.statusMessage = baseResp.has("StatusMessage") ? baseResp.get("StatusMessage").getAsString() : "";
        } else {
            // 扁平结构——BaseResp 字段在顶层
            resp.statusCode = json.has("StatusCode") ? json.get("StatusCode").getAsInt() : -1;
            resp.statusMessage = json.has("StatusMessage") ? json.get("StatusMessage").getAsString() : "";
        }

        // 保留完整响应为 data
        resp.data = json;
        return resp;
    }

    public boolean isBizSuccess(BizResponse resp) {
        return resp.statusCode == 0;
    }
}
