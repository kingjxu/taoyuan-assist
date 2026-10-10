# 桃园助手 1.0.28 免费 check 展示兜底

## 变更

- 客户端读取服务端返回的原始决策动作与 can-check 事实。只有 `action=fold`、`can-check=1` 且 JSON 顶层 `Result` 恰为“弃牌”时，悬浮窗展示改成“过牌”。
- 截图决策地址从 HTTP 切换为同域 HTTPS，避免授权头、响应动作和 can-check 事实在传输中被篡改；生产 HTTPS 路由已用无鉴权空请求验证可达并保持现有空响应协议。
- 原始响应体不改写；触发时记录不含身份信息的 `DECISION_FALLBACK` trace 日志。头缺失、动作不符、响应非法或 Hero 面对下注时沿用原结果。
- 版本更新为 `versionCode=21`、`versionName=1.0.28`，页面显示 `v1.0.28`。

## 候选验证

- Android 单测 25/25 通过，新增覆盖 free-fold 转换、面对下注、动作/Result 冲突和非法 JSON。
- `:app:testDebugUnitTest :app:assembleDebug` 通过；`scripts/check_apk.py` 通过，v2 签名有效。
- 候选 APK SHA256 为 `6fc22192233d25c33b12379a348899e4d121eeee8b665e654ae6cce3536c3489`。
- `git diff --check` 通过。

## 边界

- 兜底依赖同一 HTTPS 响应中的两个服务端事实头，不从客户端截图或显示文字自行推断下注状态。
- 本次没有 Android/鸿蒙真机，静态检查与 JVM 单测不能替代真实悬浮窗展示回验；部署后通过线上 `DECISION_FALLBACK` 日志和现场反馈继续观察。

## 待部署回验

- 官网 APK 的 HTTP 状态、大小、SHA256、versionCode/versionName、`check_apk.py`、v2 签名和证书。
- 1.0.27 可覆盖升级至 1.0.28，签名证书保持一致。
