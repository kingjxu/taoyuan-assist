# 桃园助手 1.0.21：访问官网

主界面“访问官网”从开发中提示改为通过 ACTION_VIEW 打开用户指定地址 http://www.ddbaby.site/tyzs/。没有可用浏览器或系统拒绝启动时提示手动访问，避免崩溃。版本同步为1.0.21/versionCode14，页面显示v1.0.21；其他业务逻辑未修改。

## 验证
- 既有10项单测通过，testDebugUnitTest及assembleDebug成功。
- scripts/check_apk.py通过版本、签名、动态接收器签名权限检查。
- git diff --check通过；静态核对点击监听器与完整URL一致。
- APK SHA256：0482bdf428de6e6a5ecd0614fbc4b52ae07894a68a582b0e88a844a395af2ccb。

## 限制
Debug签名APK。未连接Android/鸿蒙真机，未实测浏览器跳转和官网页面可用性；上述既有测试不覆盖新增浏览器点击行为。
