---
version: "1.2"
last_updated: "2026-09-21"
---
# 架构
MainActivity 展示本地会话额度，ApiService.getQuota 通过 GET /lyxz/gto_amount_info 获取 amount、expire_at、is_active；UserSessionManager 保存额度与授权状态。
ScreenCaptureService 通过 NetworkManager 上传截图，成功响应带 __RESULT__: 前缀经广播交给 FloatingWindowService；后者解析 JSON 的 Result 并更新 floating_window.xml 中 tv_feedback。
MainActivity.onResume 在登录有效且界面初始化后刷新额度：录制时沿用 verifyAuthorizationWhileRecording，否则 refreshQuota(false)。普通刷新防重复请求，后台解析响应，回主线程检查 Activity 与登录 token 后更新缓存和界面；自动成功不弹 Toast，失败保留原缓存并提示。
悬浮窗初始显示“等待反馈”，只有非空字符串 Result 能覆盖占位；普通状态、失败文本及非法响应不展示、不重置计时。有效结果红色加粗20sp，保留120字符/两行截断与5秒清空规则；新有效结果重置5秒计时，清空后保持空白直到下一次有效结果。

反馈广播由 ContextCompat.RECEIVER_NOT_EXPORTED 注册，覆盖全部受支持版本；Android 8–12 使用 AndroidX 自动合并的应用签名权限限制发送者，Android 13+ 使用系统未导出标记。发送端指定本应用包名。

MainActivity 每次回到前台检查悬浮窗权限，并读取 OverlayDiagnostics 中待提示错误；有问题时先在助手内弹窗，用户选择后才跳系统设置，打开失败回退应用详情页。点击进入和录屏授权返回时再次检查权限。前台收到私有状态广播立即提示，后台仅保存错误，返回后再提示；同一时间只显示一个悬浮窗诊断弹窗。用户确认或成功创建窗口后清除已记录错误。
FloatingWindowService 记录可捕获的初始化、前台服务启动、权限缺失和窗口创建异常；创建失败后停止该服务，正常停止录制再重试。仅在 addView 成功后保存窗口引用，失败不留“已创建”的假状态；清理已被系统移除的窗口时不再崩溃。服务启动调用被拒绝也由 Activity 记录。该机制不保证检测系统静默隐藏、后台杀进程或特定界面屏蔽悬浮窗，不能证明已定位/解决某款鸿蒙设备的根因。

悬浮窗位置使用 TOP | CENTER_HORIZONTAL，x=20px、y=50px；1.0.20 将顶部偏移从100px调为50px，沿用原像素单位和系统默认窗口布局处理。

1.0.21 主界面“访问官网”通过 ACTION_VIEW 打开 http://www.ddbaby.site/tyzs/；没有可用浏览器或系统拒绝启动时提示手动访问。

1.0.22 将每次截图请求结束后的固定1秒等待改为“相邻请求开始至少间隔1秒”。间隔使用 `SystemClock.elapsedRealtime()` 单调时钟计算，不受系统时间校准影响；耗时超过1秒的请求返回后立即处理最新帧，快速成功或失败则只补足剩余间隔，等待可被线程中断。上传仍由单线程同步HTTP请求串行执行，帧队列容量保持1并只保留最新帧，因此不会产生并发上传、快速失败请求风暴或历史帧堆积。
