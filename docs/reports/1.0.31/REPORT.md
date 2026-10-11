# 桃园助手 1.0.31 决策策略设置验证记录

## 变更

- “设置”弹窗新增决策策略下拉框，选项固定为“默认”和六组 VPIP/PFR 档位。
- 保存后按档位名称持久化；无效或旧配置回退为默认，用户不能自由输入频率。
- 上传截图时增加整数 `vpip`、`pfr` 字段。默认档位明确发送 `0/0`，非默认档位发送名称对应的两个数值。
- 版本升级为 `1.0.31` / `versionCode 24`。

## 验证

- `clean :app:testDebugUnitTest :app:assembleDebug` 通过，共31项单测、39个 Gradle task。
- 单测覆盖七个选项和值、选择持久化、无效索引回退、设置弹窗摘要，以及默认 `0/0` 和非默认 `42/20` 的上传 JSON 数字类型。
- 候选 APK 为 6,864,091 bytes，SHA256 `e81ba20967d0fd05e0e1548b52efcd16aa450f03315486dff737a4d62d5cb3ed`，版本为 `1.0.31` / `versionCode 24`。
- 候选 APK 通过 `check_apk.py`、APK Signature Scheme v2 和证书一致性校验；证书 SHA256 为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`。

## 边界

- 当前仅完成客户端和 ddbaby 自动化验证。上游 `/gto/decision_v2` 即使携带与旧接口相同的 `X-API-Key`，在现有 GTO 地址仍返回 HTTP 404；未将候选发布到官网，须在 v2 路由部署并完成端到端回放后发布。
- 无 Android/鸿蒙真机，不能据此声明不同厂商 Spinner 样式、设置弹窗布局或悬浮窗行为已经实测。
