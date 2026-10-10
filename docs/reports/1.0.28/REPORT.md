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

## 部署

- 2026-10-10 部署前官网 1.0.27 APK SHA256 为 `f5b80700112852b096a65004c38941e53d461975851b23f706d5c37538afefc4`；旧包备份到 `/home/ubuntu/taoyuan-backups/20261010-175004-before-1.0.28`。
- 候选上传后校验 SHA256，通过下载目录同目录临时文件原子替换。公网 `https://www.ddbaby.site/tyzs/api/download` 回验 HTTP 200、`6914626` bytes、SHA256 `6fc22192233d25c33b12379a348899e4d121eeee8b665e654ae6cce3536c3489`，与候选逐字节一致。
- 公网 APK 为 versionCode=21、versionName=1.0.28，`check_apk.py` 通过，v2 签名有效；证书 SHA256 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8` 与 1.0.27 相同，可覆盖升级。
