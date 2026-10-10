# 桃园助手 1.0.30 悬浮窗字体设置

## 变更

- 主界面在“访问官网”上方增加“设置”按钮，当前设置项为悬浮窗字体大小。
- 字号范围14–36sp，默认20sp。弹窗同时显示当前sp数值和与悬浮窗一致的黑底红色粗体示例，拖动滑块时实时更新，用户保存前即可判断实际大小。
- 保存后使用本地SharedPreferences持久化；下次创建悬浮窗时读取该值。悬浮窗已经运行时，通过应用内非导出广播立即更新字号；取消不修改配置。
- 版本更新为 `versionCode=23`、`versionName=1.0.30`。

## 验证

- Robolectric单测29/29通过。新增覆盖设置入口、预览数值与实际sp同步、持久化边界、运行中悬浮窗即时刷新，以及设置弹窗随Activity暂停/销毁清理并可在页面重建后重新打开。
- 使用JDK17、Gradle8.7执行 `:app:clean :app:testDebugUnitTest :app:assembleDebug`，39个构建任务全部成功。
- `scripts/check_apk.py`通过；APK内版本为1.0.30/code23，APK Signature Scheme v2有效。
- 候选APK为6,861,935 bytes，SHA256 `0576702ce6c0597ba7cfe3691b202f8484d0a7c0fca967e8a63c8349bb4477c3`；证书SHA256 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`，与官网1.0.29一致，可覆盖升级。
- `git diff --check`通过。

## 边界

- 当前没有Android/鸿蒙真机。自动化验证覆盖配置、预览和运行中TextView更新，不代表所有厂商系统在悬浮窗缩放后都采用相同换行、状态栏和手势导航布局。
- 设置只控制悬浮窗文字大小，不改变悬浮窗位置、颜色、显示时长、决策内容或识别链路。

## 发布

- 客户端业务提交 `95842d6`、生命周期修复提交 `eaad99e` 经 CR LGTM 后发布；发布事实记录提交为 `97711f6`，不改变APK内容。官网旧1.0.29已备份到 `/home/ubuntu/taoyuan-backups/20261010-213835-before-1.0.30`；候选上传后先核对大小和SHA，再在下载目录原子替换。
- 公网 `https://www.ddbaby.site/tyzs/api/download` 返回HTTP 200、`Content-Length=6861935`；重新下载的APK SHA256为 `0576702ce6c0597ba7cfe3691b202f8484d0a7c0fca967e8a63c8349bb4477c3`，与候选一致。
- 公网APK复验为 `versionName=1.0.30`、`versionCode=23`，`scripts/check_apk.py`通过，APK Signature Scheme v2有效；证书SHA256为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`，与官网旧版一致，可覆盖升级。
- 公网回验只证明资产、版本和签名一致；当前仍无Android/鸿蒙真机，不能据此声明厂商悬浮窗的字号换行、状态栏或手势导航布局已验证。
