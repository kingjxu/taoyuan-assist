# 桃园助手 1.0.18 变更与验证

## 行为
- 登录有效时进入/返回主界面立即查询额度；录制中复用原授权校验，非录制时自动刷新。手动刷新保留成功提示，自动成功不弹提示。普通刷新防重复，在后台解析后检查页面/登录token，再更新额度、有效期和激活状态；失败不覆盖缓存。
- 反馈悬浮窗只展示响应顶层字符串 Result。过滤缺失、空白、null、非字符串、非法JSON及上传/录制状态文字。
- 首个有效结果之前保持“等待反馈”。有效结果红色、加粗、24sp。保留原有两行/120字符限制及有效结果展示5秒后清空规则；新有效结果重新计时，无效结果不延时。
- 版本1.0.18/versionCode11，Debug签名，Android8.0+。

## 已验证
- Gradle :app:testDebugUnitTest :app:assembleDebug 成功（24秒，36任务）。4项单测通过，其中新增FloatingResultTest 3项覆盖19种输入，原有1项为模板算术测试。
- XML静态断言：等待反馈、#FF0000、bold、24sp均通过。
- APK apksigner verify通过v2签名；aapt核验包名com.example.screencaptureapp、1.0.18/versionCode11、minSdk26。
- git diff --check通过。

## 未验证与真机检查
没有连接Android设备，本次未宣称通过真机/真实账号联调。额度进入/返回前台、手动刷新、网络失败、返回时正在录制、退出页面后响应到达需真机确认；反馈需确认初始占位、有效Result样式、错误不覆盖、5秒清空、新结果重置计时和长文字截断。无服务端代码或接口契约变更。

## CR 补改
- 同步app_version为v1.0.18，登录、注册、主界面和关于弹窗共用该资源；构建后检查实际APK资源与Gradle版本一致，脚本和结果已归档。
- 接收器统一ContextCompat.RECEIVER_NOT_EXPORTED，发送端指定包名。核对实际依赖androidx.core1.13的Api26Impl字节码：NOT_EXPORTED且无显式permission时调用obtainAndCheckReceiverPermission，再带permission注册。APK已合并声明及申请DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION，保护级别signature=0x2。应用自身持有该权限，其他未共享签名的应用无法取得它。
- 重新执行单测和构建通过，apksigner与check_apk静态检查通过。adb devices无连接设备；内部广播可达和外部应用广播不可达仅核验了源码/依赖/权限链路，未声称跨UID真机测试通过。
