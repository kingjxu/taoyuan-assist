---
version: "1.0"
last_updated: "2026-09-18"
---
# 构建与验证
使用 JDK17、Gradle8.7、AGP8.6.0，compileSdk34/build-tools34.0.0；运行 :app:testDebugUnitTest :app:assembleDebug。
构建产物 app/build/outputs/apk/debug/app-debug.apk，使用 apksigner verify 和 aapt dump badging 核对签名、包名及版本。
本机 SDK 和可写 Gradle 缓存在 /tmp/taoyuan-android-sdk 和 /tmp/taoyuan-gradle。没有真机时不能声称已验证登录、悬浮窗权限和录屏全链路。
