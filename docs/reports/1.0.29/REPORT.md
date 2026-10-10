# 桃园助手 1.0.29 Hero 门控与悬浮窗拖拽

## 变更

- Hero cycle检测与服务端polerv2门控同步：先确认中央绿色牌桌表面，再要求左侧红色fold或all-in option的灰色白X，并同时存在右侧操作控件。
- 启动页彩色图标和历史面板不再误建Hero cycle；检测仍只用于时延追踪，不阻断截图、上传、识别或决策。
- 悬浮窗支持手指拖拽。移动超过系统touch slop后更新WindowManager坐标，并限制在当前屏幕范围内；轻触不会误触发拖动。
- 版本更新为 `versionCode=22`、`versionName=1.0.29`。code21曾发布后撤回，因此新包必须使用22才能覆盖已安装的1.0.28。

## 验证

- Android单测25/25通过；`:app:clean :app:testDebugUnitTest :app:assembleDebug`通过。
- `scripts/check_apk.py`通过，APK内版本与页面均为1.0.29；v2签名有效。
- 候选APK为6,858,151 bytes，SHA256 `26406916cf8de1815469b4f8388b909c5988dc2e8d2dcbece5cbe32e282f3b57`；证书SHA256 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`，与1.0.27一致，可覆盖升级。
- 单测覆盖启动页、历史面板、红色fold、灰色白X、cyan+白色图标、画布边界及拖拽顶部边界；`git diff --check`通过。

## 边界

- 本轮没有Android/鸿蒙真机，Robolectric验证不能替代真实手势导航、状态栏和厂商悬浮窗实现。
- 拖拽只改变本次服务生命周期内的位置，没有新增持久化坐标、后台请求或权限。

## 部署

代码评审通过后，备份官网现有APK，校验候选版本、SHA和签名后在同目录原子替换；再从公网下载回验HTTP状态、大小、SHA、版本和证书。
