---
version: "1.7"
last_updated: "2026-10-06"
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

1.0.23 移除成功请求后的1秒节流等待。上传仍由单线程同步HTTP请求串行执行；当前成功请求返回后立即处理队列中的最新帧。帧队列容量保持1并持续替换旧帧，因此不会产生并发上传或历史帧堆积，实际上传频率由单次请求耗时和新帧产生速度限制。连续失败仅执行250ms起、最大2秒的可中断退避，成功后立即清零，避免网络或服务端快速失败时形成请求风暴；该退避不影响正常成功链路时延。

1.0.24 移除 `READ_MEDIA_IMAGES`、`READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE` 和 legacy external storage 配置，主界面不再申请照片/存储权限。识别链路继续通过 MediaProjection 获取屏幕帧并直接编码上传，不读取或写入公共相册；系统录屏授权和悬浮窗设置流程保持不变。

1.0.25 精简成功和状态型 Toast：登录、验证码发送、注册自动登录、额度刷新、授权码激活、录制停止、AAPoker 启动和悬浮窗授权成功不再额外弹出短提示，由界面状态或页面跳转反馈结果。保留浏览器打开失败、AAPoker 缺失、录屏开始、录屏授权拒绝和缺少媒体投影授权五个需要用户感知或处理的 Toast。

1.0.26 为每个上传帧生成不含身份信息的 trace id，并使用 `elapsedRealtime` 记录采集、Bitmap 成帧、容量1最新帧队列、被替换帧数、JPEG、请求体写入、HTTP、收到响应和悬浮窗下一帧展示。服务端通过响应头返回 bind、授权、识别、请求准备、决策和总耗时。展示完成的追踪随下一次正常截图请求回传服务端，不新建并发上传；最后一次展示若没有后续请求，只保留本机 `TRACE_COMPLETE` 日志。追踪失败或无效不改变原业务响应和队列语义。

1.0.27 在每张已生成 Bitmap 上用 polerv2 左侧红色 fold ROI 与右侧操作按钮 ROI 做轻量 HSV 等价颜色门控；检测仅用于时延追踪，不控制截图或上传。连续可操作帧共用随机 `decision_cycle_id`，连续两张非 Hero 帧结束 cycle。服务端只把同一 cycle 的首次实际悬浮窗展示计入 `client_complete`，后续展示标记为 repeat；decided、其他玩家阶段和重复 Hero 不进入首次结果指标。旧 v1 trace 保持兼容。
