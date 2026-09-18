# 桃园助手 1.0.20：悬浮窗上移

- 顶部偏移 y 从100px调至50px，沿用原像素单位、TOP | CENTER_HORIZONTAL 与 x=20px。实际屏幕位置仍受系统窗口布局影响。
- 版本同步为1.0.20/versionCode13，页面显示v1.0.20。
- 保留20sp红色加粗、初始等待反馈、只显示有效Result、有效结果5秒清空与权限诊断。

## 验证
- testDebugUnitTest、assembleDebug成功；既有10项测试全部通过，未为单行位置调整增加重复测试。
- scripts/check_apk.py通过版本、签名及动态接收器签名权限检查。
- APK SHA256：ba8cc861dea52246da9080779711e7fffe256e7dd66715f922debf8b110fba11。
- git diff --check通过。

## 限制
无Android/鸿蒙真机验证；实际位置和可读性请安装后确认。本次为Debug签名APK。
