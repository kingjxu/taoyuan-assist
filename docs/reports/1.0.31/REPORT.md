# 桃园助手 1.0.31 决策策略设置验证记录

## 变更

- “设置”弹窗新增决策策略下拉框，选项固定为“默认”和六组 VPIP/PFR 档位。
- 保存后按档位名称持久化；无效或旧配置回退为默认，用户不能自由输入频率。
- 上传截图时增加整数 `vpip`、`pfr` 字段。默认档位明确发送 `0/0`，非默认档位发送名称对应的两个数值。
- 版本升级为 `1.0.31` / `versionCode 24`。

## 验证

- `clean :app:testDebugUnitTest :app:assembleDebug` 通过，共31项单测、39个 Gradle task。
- 单测覆盖七个选项和值、选择持久化、无效索引回退、设置弹窗摘要，以及默认 `0/0` 和非默认 `42/20` 的上传 JSON 数字类型。
- 使用与官网旧版一致的签名环境重新 clean 构建，候选 APK 为 6,864,059 bytes，SHA256 `040f3d77b80ace349b39f0cf238873acd96d256ccc9a342f96ca421c3ba42c70`，版本为 `1.0.31` / `versionCode 24`。
- 候选 APK 通过 `check_apk.py`、APK Signature Scheme v2 和证书一致性校验；证书 SHA256 为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`。
- 2026-10-11 18:28（+08:00）由 ddbaby 候选调用真实上游：白名单档位 `42/20` 映射为 `vvip=42,pfr=20` 后，`POST /gto/decision_v2` 返回 HTTP 200；默认 `0/0` 继续调用原 `/gto/decision`，同样返回 HTTP 200。两份响应均可解析且动作非空。

## 边界

- 当前完成客户端自动化验证、ddbaby 候选隔离构建和候选到真实上游的联调；尚未把 ddbaby 候选部署到生产，也未将客户端候选发布到官网，因此不把上述证据表述为生产端到端回放。
- 部署和官网发布须在用户明确批准后执行；发布后仍需分别回验默认档位、至少一个非默认档位、服务健康与公网 APK 哈希。
- 无 Android/鸿蒙真机，不能据此声明不同厂商 Spinner 样式、设置弹窗布局或悬浮窗行为已经实测。

## 发布

- 客户端业务提交 `9b2848f`、真实联调记录提交 `ac9f491` 经 CR LGTM 且用户明确批准后发布；本次只发布客户端，未替换或重启 ddbaby 服务端。
- 发布前官网 1.0.30 的 SHA256 为 `0576702ce6c0597ba7cfe3691b202f8484d0a7c0fca967e8a63c8349bb4477c3`，已备份到 `/home/ubuntu/taoyuan-backups/20261011-184605-before-1.0.31`。候选上传后先核对 SHA，再在下载目录同文件系统原子替换，目标权限为0644。
- 公网 `https://www.ddbaby.site/tyzs/api/download` 返回 HTTP 200、`Content-Length=6864059`；重新下载 SHA256 为 `040f3d77b80ace349b39f0cf238873acd96d256ccc9a342f96ca421c3ba42c70`，与冻结候选逐字节一致。
- 公网 APK 复验为 `versionName=1.0.31`、`versionCode=24`，`scripts/check_apk.py` 通过，APK Signature Scheme v2 有效；证书 SHA256 为 `82042b739b8e4006d48f2a351c483a9235e82271e49dea12614b416679174ec8`，与官网旧版一致，可覆盖升级。
- 当前生产 ddbaby 对包含 `vpip=0,pfr=0` 的兼容 smoke 返回 HTTP 200，证明旧请求绑定层接受新增字段；该无鉴权空图片 smoke 不代表真实识别、授权或非默认 v2 决策已经上线。服务端仍须另行获得用户批准后部署。
