---
version: "1.1"
last_updated: "2026-10-10"
---
# 业务口径
- 额度：额度接口 data.amount，本地展示使用 session.getEffectiveQuota()，有效期 data.expire_at，激活状态 data.is_active。
- Result：截图上传接口返回 JSON 顶层、大小写敏感的 Result 字段，供悬浮窗显示；不是状态码或完整响应。
- 有效反馈：非空且不全为空白的字符串 Result；缺失、null、对象、数组、数字、布尔值和非法 JSON 不作反馈。
- 等待反馈：当前悬浮窗首次有效 Result 之前的占位文字，上传状态和异常不覆盖它。
- 免费 check 兜底：服务端已校验请求中 Hero 当前投入等于本轮最大投入，且原始决策仍返回 fold 时，1.0.28 客户端把本地“弃牌”展示改为“过牌”。必须同时命中服务端原始动作与 can-check 响应头；不能只按中文 Result 或客户端图像猜测。
