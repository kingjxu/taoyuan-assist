# 桃园助手 1.0.25 Toast 精简

## 变更

- 版本更新为 `versionCode=18`、`versionName=1.0.25`，页面显示 `v1.0.25`。
- 删除 8 个已有明确界面反馈的成功/状态 Toast：登录成功、验证码已发送、注册成功并自动登录、额度已刷新、授权码激活成功、录制已停止、AAPoker 已启动、悬浮窗权限已获取。
- 保留 5 个需要用户感知或处理的 Toast：浏览器打开失败、AAPoker 未安装、录屏已开始、录屏授权被拒绝、需要媒体投影权限。
- 额度刷新的手动和自动路径共用同一个无成功 Toast 的实现；失败仍通过原有对话框告知用户。

## 验证

- `:app:testDebugUnitTest` 通过：11 项测试，0 failure，0 error。
- `:app:assembleDebug` 通过；`scripts/check_apk.py` 对候选包检查通过。
- 候选 APK 为 `versionCode=18`、`versionName=1.0.25`，v2 签名通过，证书 SHA256 为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`。
- 候选 APK SHA256 为 `b5438355ab18f3a8f7d83ffae3becb39d1d75dc6dacedd86c546060d7c6720aa`。
- 源码静态统计只余 5 个生效的 `Toast.makeText` 调用点，内容与上述保留清单一致。
- APK 权限清单仍不包含照片或存储权限；`git diff --check` 通过。
- 没有连接 Android/鸿蒙真机，不宣称已完成 Toast 视觉、系统录屏授权、悬浮窗设置或端到端识别实测。

## 官网部署

- 2026-10-03 部署前官网 1.0.24 APK SHA256 为 `a751ed1a2e60dd5e6c66c70a3ab76e3167982d223ce8034d6291a3315a346efc`；原 APK 和校验值已备份到 `/home/ubuntu/taoyuan-backups/20261003-204922-before-1.0.25`。
- 候选 APK 先上传到下载目录的临时文件，校验 SHA256 后使用原子 `rename` 替换官网 APK；官网首页未修改。
- 公网下载回验 HTTP 200，SHA256 为 `b5438355ab18f3a8f7d83ffae3becb39d1d75dc6dacedd86c546060d7c6720aa`，与候选包一致。
- 公网包回验为 `versionCode=18`、`versionName=1.0.25`，v2 签名通过，证书 SHA256 为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`。
- 公网包权限清单仍不包含照片或存储权限；`check_apk.py` 对公网包检查通过。
