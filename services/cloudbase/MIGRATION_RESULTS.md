# CloudBase AI 迁移验收

2026-10-17：**自动部署、真实 DeepSeek 联调及原证书 Signed Release APK 已完成。平板未连接，真平板验收待用户补验。**

- 正式交付：[APK 与验收说明](../../deliverables/cloudbase-release/FINAL_ACCEPTANCE.md)。
- 云端、预算及维护：[部署结果](../../deliverables/cloudbase-release/CLOUD_DEPLOYMENT_RESULT.md)。
- 新函数 `wildflower-ai` 与独立 `/wildflower-ai` 网关已启用，原 health 和 Worker 保留；计数文档不重置，客户端 `ADMINONLY`。
- Node 20.19 **43/43**；JVM Debug/Release 各 **126/126**；网络设备合同 **14/14**；针对实际非 Debug APK 的三轮真实问答、自然表达及缓存 **1/1**。
- 修复问答正文在 UI 主线程读取导致的 `NetworkOnMainThreadException`；修复前失败记录保存在 `verification/main-thread-regression-before.txt`，修复后延迟响应回归通过。
- 最终 APK 使用 CloudBase，签名与原证书一致；三项真实服务端秘密的 APK 解包匹配数为 0。详细日志、路由与额度快照在 `verification/`。
- 本次未修改 Room、文化资料、地图、识别、诗笺或 UI 布局；未 reset / clean / commit / push，既有工作保留。
