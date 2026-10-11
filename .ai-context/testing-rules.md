---
version: "1.4"
last_updated: "2026-10-11"
---
# 构建与验证
使用 JDK17、Gradle8.7、AGP8.6.0，compileSdk34/build-tools34.0.0；运行 :app:testDebugUnitTest :app:assembleDebug。
构建产物 app/build/outputs/apk/debug/app-debug.apk，使用 apksigner verify 和 aapt dump badging 核对签名、包名及版本。
本机 SDK 和可写 Gradle 缓存在 `/tmp/taoyuan-android-sdk` 和 `/tmp/taoyuan-gradle-ready`。没有真机时不能声称已验证登录、悬浮窗权限和录屏全链路。

可升级 APK 必须显式使用长期保存的 Android 用户目录 `/data00/home/xujin.xj/.android` 构建，并在交付前核对签名证书 SHA256。禁止使用临时 `ANDROID_USER_HOME` 生成发布候选，否则临时 debug key 丢失后无法继续覆盖升级。

`scripts/check_apk.py <build-tools目录> <APK>` 检查Gradle版本、页面app_version资源、APK内实际字符串、签名与动态广播签名权限声明；它不替代跨应用广播真机验证。

OverlayWindowTest 使用 Robolectric4.13/API28 验证服务窗口创建失败重试、权限缺失记录、前台弹窗、后台待提示、设置入口以及等待/5秒计时。字体设置必须验证按钮位于“访问官网”上方、滑块数值与实际sp预览同步、14–36sp边界、保存持久化、运行中悬浮窗收到应用内设置广播后立即刷新，以及设置弹窗在 Activity 暂停或销毁时关闭并清空引用、页面重建后可以重新打开。模拟环境须显式授予 APK 已声明的应用签名广播权限和普通权限，不放宽生产注册策略。测试通过不代表鸿蒙真机覆盖；MainActivity 弹窗测试只创建页面、直接驱动提示方法和私有广播，不调用 onResume 发真实额度网络请求。

策略设置必须验证下拉框仅包含默认和六组产品档位、保存后持久化准确的 VPIP/PFR、非法本地索引回退默认，以及上传 JSON 将 `vpip/pfr` 作为数字发送；默认值必须明确为 `0/0`。无服务端联调时，客户端单测不能声明 v2 决策已端到端可用。

设置弹窗样式回归必须验证标题、说明和策略选中项相对各自背景至少达到4.5:1对比度，并检查显式下拉箭头、下拉背景、标题/策略项字号和保存按钮主题色；这些资源级测试不能替代真机视觉验收。

HeroTurnDetector 必须与服务端 polerv2 的 Hero 轮次门控同步：中央绿色牌桌表面、左侧红色 fold 或灰色白 X、右侧可操作控件缺一不可；启动页、历史面板和结束页必须返回 false。该检测只用于 cycle 时延追踪，不得阻断截图上传。悬浮窗拖拽测试必须覆盖超过 touch slop 后更新坐标、顶部和屏幕边界限制；无真机时不得宣称不同系统状态栏/手势导航下的位置已实测。
