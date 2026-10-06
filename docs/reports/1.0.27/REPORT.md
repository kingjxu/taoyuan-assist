# 桃园助手 1.0.27 Hero 首次结果时延追踪

## 变更

- 客户端在已生成的截图 Bitmap 上扫描 polerv2 左侧 fold 和右侧操作按钮 ROI，以与服务端相同的颜色阈值判断 Hero 操作控件是否可见。
- 连续 Hero 帧共用随机 `decision_cycle_id`；连续两张非 Hero 帧结束 cycle。该检测只记录时间，不拦截截图、上传、识别或决策。
- v2 trace 上报 Hero 控件首次检测时间、cycle 起始帧和首次实际悬浮窗展示；v1 trace 保持兼容。
- 版本更新为 `versionCode=20`、`versionName=1.0.27`，页面显示 `v1.0.27`。

## 验证

- Android 单测 21/21 通过，覆盖左右按钮组合、结束页单右按钮、青色 check + 白色图标、画布边界、cycle 合并/切分、v1/v2 序列化及首次展示去重。
- 2026-10-06 的 52 张现场 Hero 图使用与 Java 实现相同的整数 HSV 和三像素采样规则重放：47 张可操作页为 true，5 张结束页为 false；false 时间为 `17:48:52`、`17:48:54`、`17:49:43`、`17:49:44`、`17:49:45`。
- `:app:testDebugUnitTest :app:assembleDebug` 通过；`scripts/check_apk.py` 通过。
- 候选 APK SHA256 为 `f5b80700112852b096a65004c38941e53d461975851b23f706d5c37538afefc4`，v2 签名有效；证书 SHA256 为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`，与 1.0.26 相同，可覆盖升级。
- `git diff --check` 通过。

## 边界

- 本次没有 Android/鸿蒙真机，不能宣称 UI 首次轮到 Hero 至悬浮窗首次展示的真实耗时已经取得；发布后需等待 1.0.27 真实 `client_complete`。
- 检测仅支持当前 polerv2 竖屏画布比例。比例不支持或检测失败时继续原上传链路，只缺少 cycle 级时延数据。

## 部署

待代码复审通过后补充官网备份、原子替换和公网 APK 回验记录。
