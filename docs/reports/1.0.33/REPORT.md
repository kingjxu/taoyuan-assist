# 桃园助手 1.0.33 Hero 重复请求收敛

## 变更

- 保持截图队列容量为1以及单线程串行上传，不积压历史帧。
- 首次 Hero 帧立即请求；合法决策成功返回后，抑制同一连续 Hero 可见段内的后续请求。
- 任意一张非 Hero 帧都会重新允许下一次 Hero 请求，避免快速再加注后漏掉 Hero 的第二次行动。
- 上传失败、超时和无有效 `Result` 时继续使用最新帧重试；持续 Hero 每6秒允许一次安全探测。
- 请求闸门使用独立 generation，不改变 `decision_cycle_id` 的观测口径，不修改 ddbaby 服务端。
- 版本更新为 `versionCode=26`、`versionName=1.0.33`。

## 验证

- `HeroRequestGateTest` 覆盖成功抑制、单张非 Hero 重新武装、失败重试、旧成功/失败响应不能覆盖新 generation、6秒保险和 reset。
- Hero 检测、cycle 追踪、时延序列化相关定向单测通过。
- `:app:testDebugUnitTest :app:assembleDebug` 通过；40/40 单测通过，无失败、错误或跳过。
- `scripts/check_apk.py` 通过；APK内版本为1.0.33/code26，APK Signature Scheme v2有效，证书SHA256为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`，与已发布1.0.32一致，可覆盖升级。
- 最终 clean 构建 APK SHA256为 `f942589b9c86b0c7dab5d8e9c8a72e32ad6651588b0e5e47b13165f02269ce43`。

## 边界

- 当前环境没有 Android/鸿蒙真机，不能宣称录屏、手动操作和快速再加注场景已完成真机验证。
- 只靠截图无法证明两个视觉上连续的 Hero 画面一定属于同一行动机会；本实现以任意非 Hero 帧和6秒安全探测优先保证不会永久漏请求。

## 官网发布

- 业务提交 `401f2d7` 经 CR LGTM 后快进推送到远端 `main`；`ls-remote` 核对远端 SHA 一致。
- 发布前官网 1.0.32 为 6,869,609 bytes，SHA256 `fa3d8e4b99a96236824634b5f19b307f06a7df2c3968b6ce81c50c187dde6b25`；已备份到 `/home/ubuntu/taoyuan-backups/20261013-115605-before-1.0.33`。
- 最终 clean 构建候选先在服务器核对 SHA，再以同目录 `mv` 原子替换，目标权限保持 0644。
- 公网 `https://www.ddbaby.site/tyzs/api/download` 返回 HTTP 200、`Content-Length=6870953`；重新下载 SHA256 为 `f942589b9c86b0c7dab5d8e9c8a72e32ad6651588b0e5e47b13165f02269ce43`，与冻结候选逐字节一致。
- 公网 APK 复验为 `versionName=1.0.33`、`versionCode=26`，`scripts/check_apk.py` 通过，APK Signature Scheme v2 有效；证书 SHA256 为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`，与 1.0.32 一致，可覆盖升级。
