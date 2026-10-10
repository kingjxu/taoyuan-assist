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

- 客户端业务提交 `847ff00` 经 CR LGTM 后发布；只记录发布事实的提交为 `5266dc1`，不改变APK内容。官网旧APK与旧问题页备份在 `/home/ubuntu/taoyuan-backups/20261010-210905-before-1.0.29`，候选校验后在原目录原子替换。
- 公网 `https://www.ddbaby.site/tyzs/api/download` 返回HTTP 200、`Content-Length=6858151`；重新下载的APK SHA256为 `26406916cf8de1815469b4f8388b909c5988dc2e8d2dcbece5cbe32e282f3b57`，与候选一致。
- 公网APK复验为 `versionName=1.0.29`、`versionCode=22`，`scripts/check_apk.py`通过，APK Signature Scheme v2有效；证书SHA256为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`，与1.0.27一致。
- 本轮仍无Android/鸿蒙真机，线上验收只证明业务提交 `847ff00` 构建的公网资产、版本和签名一致；没有把公网回验视为拖拽已通过真机验证，实际拖拽手势、状态栏和手势导航边界仍需真机观察。
