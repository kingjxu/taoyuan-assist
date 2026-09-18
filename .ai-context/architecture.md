---
version: "1.0"
last_updated: "2026-09-18"
---
# 架构
MainActivity 展示本地会话额度，ApiService.getQuota 通过 GET /lyxz/gto_amount_info 获取 amount、expire_at、is_active；UserSessionManager 保存额度与授权状态。
ScreenCaptureService 通过 NetworkManager 上传截图，成功响应带 __RESULT__: 前缀经广播交给 FloatingWindowService；后者解析 JSON 的 Result 并更新 floating_window.xml 中 tv_feedback。
MainActivity.onResume 在登录有效且界面初始化后刷新额度：录制时沿用 verifyAuthorizationWhileRecording，否则 refreshQuota(false)。普通刷新防重复请求，后台解析响应，回主线程检查 Activity 与登录 token 后更新缓存和界面；自动成功不弹 Toast，失败保留原缓存并提示。
悬浮窗初始显示“等待反馈”，只有非空字符串 Result 能覆盖占位；普通状态、失败文本及非法响应不展示、不重置计时。有效结果红色加粗24sp，保留120字符/两行截断与5秒清空规则；新有效结果重置5秒计时，清空后保持空白直到下一次有效结果。

反馈广播由 ContextCompat.RECEIVER_NOT_EXPORTED 注册，覆盖全部受支持版本；Android 8–12 使用 AndroidX 自动合并的应用签名权限限制发送者，Android 13+ 使用系统未导出标记。发送端指定本应用包名。
