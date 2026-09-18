---
version: "1.0"
last_updated: "2026-09-18"
---
# 业务口径
- 额度：额度接口 data.amount，本地展示使用 session.getEffectiveQuota()，有效期 data.expire_at，激活状态 data.is_active。
- Result：截图上传接口返回 JSON 顶层、大小写敏感的 Result 字段，供悬浮窗显示；不是状态码或完整响应。
- 有效反馈：非空且不全为空白的字符串 Result；缺失、null、对象、数组、数字、布尔值和非法 JSON 不作反馈。
- 等待反馈：当前悬浮窗首次有效 Result 之前的占位文字，上传状态和异常不覆盖它。
