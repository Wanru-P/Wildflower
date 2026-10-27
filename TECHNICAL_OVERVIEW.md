# 草木知时 · Wildflower Lens 技术概要

Android 原生应用，包名 `com.floralens`，Kotlin / Jetpack Compose，JDK 17，Gradle Wrapper 8.13，minSdk 26 / compileSdk、targetSdk 36。

| 能力 | 实现与职责 |
|---|---|
| 相机 | CameraX 预览、拍照及生命周期；系统相机用于追加观察 |
| 植物识别 | 本地 TFLite；可选 Pl@ntNet 在线识别；中文名称映射仅影响展示，不改模型输出 |
| 个人档案 | Room v3；Species 为公共物种，Plant 为具体植株，Observation 为单次观察 |
| 收藏 | 显式确认后保存；临时拍摄与收藏分离，照片保存及档案创建有失败补偿 |
| 图片管理 | PendingCaptureStore / PhotoStore；相册内容复制到受控存储，追加不覆盖历史照片 |
| 设置 | DataStore 保存引导与识别模式等配置，无账号、无云同步 |
| 文化 | 本地 JSON 50 条，稳定物种标识匹配；核验状态与发布批准分开，导出只允许批准内容 |
| 四季 | 按 Observation 时间计算年份和季节，不使用物种花期推断 |
| 地图 | Google Maps 在线地图与本地 Canvas 离线投影；仅使用已保存的观察坐标 |
| 数字诗笺 | 内容、模板、渲染、导出与分享分离；三套 Canvas 作品，真实 1600×2400 PNG |
| 保存与分享 | MediaStore、SAF、FileProvider 临时读取授权；打开分享不等于用户已经发送 |
| 其他 | Hilt 注入、WorkManager 养护提醒与通知深链、原有叶片检查入口 |

## 原创与上游

基于 [FloraLens](https://github.com/vedant7356/FloraLens)。相机、识别和原有存储/地图等能力属于上游复用，不能宣称为从零原创；自然探索视觉、具体植株持续观察、四季归档、文化匹配及三模板诗笺是本项目的扩展工作。上游授权核查、素材与字体声明沿用仓库已有文件，不能以本说明替代许可证或赛事资格审查。

## 发布安全

密钥来自被忽略的 `local.properties`，不写入源代码或展示材料。Android 客户端 APK 不能充当密钥保险箱：正式发布需限制 API 配额、包名及最终签名证书；不能把 Debug 证书限制直接视为 Release 可用。当前没有可用 Release 签名，不自动生成新密钥、不更换签名、不更改包名。

本轮没有改动已验收的布局、字体、颜色、素材、Room schema、模型或文化原文。
