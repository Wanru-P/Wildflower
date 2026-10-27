# CloudBase 迁移部署步骤

本包仅部署新的 **wildflower-ai**。原 wildflower-health 和 Cloudflare Worker 保留。CloudBase 真实验证通过后，Android 默认入口已切换到新函数；最终结果见 `deliverables/cloudbase-release/FINAL_ACCEPTANCE.md`。

本项目的已确认配置：环境 ID `wildflower-ai-test-d5cge7e1432c7`，地域 `ap-shanghai`，HTTPS 网关 `https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com`。

2026-10-17 自动部署：新函数与独立路由已创建，原 `/health` 保持 HTTP 200。新函数 `/wildflower-ai/health` 已返回 `ready`，电脑真实三轮问答、上下文回忆及自然表达通过；证据在 `verification/live_provider.json`。`cloudbaserc.json` 仅描述新函数和非敏感配置，不要对整个环境执行覆盖式部署。

## 自动化维护入口

已登录的 CLI 3.8.7 可由 `scripts/deploy-cloudbase.mjs` 复用临时凭据。管理 SDK 固定在 `tools/package-lock.json`，不打入云函数 ZIP 或 APK。脚本只操作指定环境和新函数，复用已有密钥，不输出秘密，不重置累计额度：

```powershell
node services/cloudbase/scripts/deploy-cloudbase.mjs
node services/cloudbase/scripts/deploy-cloudbase.mjs --inspect-ledger
# 仅更新新函数；部署保持暂停，再探测路由后开启：
node services/cloudbase/scripts/deploy-cloudbase.mjs --deploy
node services/cloudbase/scripts/verify-live.mjs --base-url https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com/wildflower-ai
node services/cloudbase/scripts/deploy-cloudbase.mjs --enable
```

Windows 本地重试凭据使用当前用户 DPAPI 加密，保存在项目外 `%LOCALAPPDATA%/Wildflower/CloudBase/`；正式签名凭据在 `%LOCALAPPDATA%/Wildflower/Signing/`。不输出、不复制这些文件到交付包。现用 CloudBase 服务端 Key 到期时间为 **2027-01-15**，须在控制台到期前替换新函数的变量。部署失败重试产生的 3 个未使用服务端 Key，用户明确选择保留并自行处理，脚本没有删除。

以下仍保留控制台首次配置/故障恢复步骤；已经存在的集合、额度文档、函数和路由不要重复初始化。

## 1. 控制台准备

1. 登录腾讯云 CloudBase，选择上海区域现有 **wildflower-ai-test**，核对完整 ID 为 `wildflower-ai-test-d5cge7e1432c7`（不是仅核对显示名称）。
2. 在该环境的文档型数据库中新建集合 `wildflower_ai_limits`，客户端权限设为所有用户不可读写，仅服务端操作。仅首次创建时导入 `services/cloudbase/quota-seed.json`，确认文档 ID 为 `global-v1`。不要覆盖或删除已有累计额度记录。
3. 在环境「API Key / 服务端 API Key」管理中创建仅供云函数使用的服务端 Key，确认可读写本环境的计数集合。它不是客户端 Publishable Key，也不是 DeepSeek Key。不能将它或请求头中的 CloudBase context 交给 Android。
4. 不需要购买套餐或新建付费实例。若免费环境未开放文档数据库/事务，停在这里并报告，不能以进程内计数冒充跨实例预算保护。

HTTP 函数不会像事件函数一样自动注入数据库管理员凭证，必须显式配置 SDK 鉴权；参见[官方 HTTP 函数资源调用说明](https://docs.cloudbase.net/cloud-function/resource-integration/cloudbase)。这里用一个计数文档的事务替代 Durable Object，不存植物档案、照片、问答、观察备注或文化内容。

## 2. 新建并上传函数

在「云函数」中新建 **HTTP 云函数** `wildflower-ai`，不要选择事件函数、不要覆盖 `wildflower-health`。

- 运行环境：Node.js **20.19**；执行超时 **60 秒**；内存 256 MB；单实例并发 3。
- 上传 `services/cloudbase/dist/wildflower-ai.zip`。ZIP 根目录应直接看到 `scf_bootstrap`、`index.mjs`、`package.json` 和 `node_modules`，不要再嵌套一层目录。
- 包内 bootstrap 使用 `/var/lang/node20/bin/node`，监听 `0.0.0.0:9000`。已设置 LF 和 Unix 可执行权限。参见[官方 HTTP Node 快速开始](https://docs.cloudbase.net/cloud-function/quickstart/httpfunc/nodejs)、[运行环境路径](https://docs.cloudbase.net/cloud-function/runtime-support)。
- 仅创建函数和上传 ZIP 不等于线上验收通过。

在新函数的环境变量页面配置：

| 变量 | 值 |
|---|---|
| `DEEPSEEK_API_KEY` | 现有真实 DeepSeek Key，仅填控制台 |
| `DEEPSEEK_MODEL` | `deepseek-flash` |
| `CLOUDBASE_ENV_ID` | `wildflower-ai-test-d5cge7e1432c7` |
| `CLOUDBASE_APIKEY` | 第 3 步创建的服务端 CloudBase Key |
| `SESSION_SIGNING_KEY` | 新生成的随机签名材料，至少 48 字符；不是临时测试口令 |
| `PUBLIC_BASE_PATH` | `/wildflower-ai` |
| `AI_ENABLED` | 先设 `false`；数据库与路由配置完成后设 `true` 并发布 |

可以在本机 PowerShell 运行以下命令生成新的签名材料，结果仅粘贴到函数环境变量，不放源码、APK、报告或聊天记录：

```powershell
node -e "process.stdout.write(require('node:crypto').randomBytes(48).toString('base64url'))"
```

默认保留：每日 100 次、每安装每日 20 次、跨实例并发 3、每日预留额度 1000 毫元、累计 10000 毫元。自然表达每次预留 5 毫元，问答每次 20 毫元，失败也不退回预留额；实例重启不重置累计值。`AI_ENABLED=false` 为紧急关闭开关。额度是代理预留计数，不是腾讯云/DeepSeek 实际账单封顶；实际价格、云资源账单须在对应控制台管理。

## 3. 独立 HTTP 网关路由

在环境「HTTP 网关 / HTTP 访问服务」增加一条独立路径：

| 配置 | 设置 |
|---|---|
| 触发路径 / 前缀 | `/wildflower-ai` |
| 资源类型 | HTTP 云函数（WEB_SCF） |
| 目标资源 | **wildflower-ai** |
| 访问 | HTTPS 公网访问；会话由本函数认证 |
| 平台额外鉴权 | 不要求 CloudBase 客户端登录，否则 Android 无法取得匿名安装会话 |
| 路径透传 | 建议开启，保留完整路径；也兼容仅剥离 `/wildflower-ai` 前缀 |

不要改根路由或 health 路由，不要分别建立会把整个路径吞掉的精确 `/v1/chat`、`/v1/nature` 路由。网关路径语义参见[官方路由配置](https://docs.cloudbase.net/cli-v1/gateway)。

本环境的真实网关映射如下：

| 外部请求 | 透传后 | 仅剥离前缀后 |
|---|---|---|
| `https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com/wildflower-ai/health` | `/wildflower-ai/health` | `/health` |
| `POST …/wildflower-ai/v1/session` | `/wildflower-ai/v1/session` | `/v1/session` |
| `POST …/wildflower-ai/v1/chat` | `/wildflower-ai/v1/chat` | `/v1/chat` |
| `POST …/wildflower-ai/v1/nature` | `/wildflower-ai/v1/nature` | `/v1/nature` |

两种形式本包都接受。错误的 `POST /` 必须返回 404，不能猜测它原来是问答还是自然表达。匿名会话是短期安装凭证，不等同于实名用户登录或设备证明。为避免信任可伪造的转发头，签发限流使用连接来源；若网关统一代理 IP，多台设备会共享每日 8 次签发限额。

## 4. 电脑真实验证（云端就绪后）

在项目根目录运行，参数只填服务根地址，不要附加 `/v1/chat`：

```powershell
node services/cloudbase/scripts/verify-live.mjs --base-url https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com/wildflower-ai
node services/cloudbase/scripts/verify-live.mjs --base-url https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com/wildflower-ai --live
```

第一条只检查健康（允许已配置的暂停状态）、两个接口的 401 和未知路径的 404，不调用模型。第二条只接受 `ready`，签发会话，验证非法输入/超长输入，再真实发起连续三轮问答（第三轮核对前文计划名称）与一次自然表达，总共最多 4 次生成，不自动重试、不放开额度。

结果在 `services/cloudbase/verification/`；只保存状态、时延和通过情况，不保存 Key、token、问题或答案。`live_provider.json` 的 `passed=true` 才允许切入口。超时处理已在本机受控延迟请求中测试，真实云端超时不能靠这个本地结果冒充；若线上未自然发生超时，应标为该云端异常场景未触发。

常见阻塞：健康 503→检查四个服务端配置；会话 503/rate_guard_unavailable→检查数据库种子文档、服务端 Key 的权限和环境 ID；401→检查是否误开启网关额外鉴权；404→检查路由目标及路径透传；429→保留额度限制并检查计数，不删除计数文档；502/invalid_output→模型结果没有满足现有内容约束，不应放宽审核或伪造替代。

## 5. 切换并签名 Release（验证后）

现有 Android 默认已切换到经过真实验证的 CloudBase。代码支持保留 CloudBase 前缀、`/v1/session` 短期凭证、过期/失效会话刷新，没有内置供应商 Key 或临时口令。旧 Worker 仍可通过显式 Gradle 参数作为回退，不是默认入口。

沿用已有正式签名的四个 `WILDFLOWER_RELEASE_*` 进程环境变量，然后执行：

```powershell
./services/cloudbase/scripts/build-cloudbase-release.ps1 -BaseUrl https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com/wildflower-ai
```

脚本先检查签名配置，再真实跑两接口；只有全部通过才以 `-PnatureProxyOrigin=...` 构建正式 APK。不修改默认配置、不会删除旧 Worker。生成位置 `app/build/outputs/apk/release/app-release.apk`，并打印文件 SHA256；签名必须与既有正式证书 SHA256 `6CB523AFB91BD5FB13CB2AB7D3D7D3DBFE00F697B7C5A8AC645AB3C8291C73C1` 一致。此阶段未生成 CloudBase 版正式 APK时，不应把此前 APK 当迁移成果。

## 6. 平板最终验收

1. 用同签名 APK 覆盖安装，不卸载、不清除数据；浏览器先访问新 `/health`。
2. 从植物详情进入花间问答，同意后连续问三轮，确认后两轮引用前文，错误时不出现模拟成功。
3. 进入植物文化，点击生成 AI 原创短诗；已有缓存时点「重新生成 AI 原创」。确认「AI 原创 · DeepSeek · 非古典文献」标识，保留实际页面截图及服务端仅含 requestId/route/status/durationMs 的日志。
4. 检查断网提示、恢复联网后重试、冷启动和原植物档案。电脑真实调用通过不等于平板通过，模拟器合同测试也不等于真实模型调用。

## 协议不变与本地复现

- 会话 `POST /v1/session`：`{installId: UUIDv4}` → `{token,expiresAt}`（毫秒）。新签名会话格式对 Android 是不透明字符串。
- 问答 `/v1/chat`：`{scientificName,chineseName,certain,question,history:[{role,content}]}` → `{answer,source:"ai_answer",version:1}`。
- 自然表达 `/v1/nature`：`{scientificName,chineseName,style}` → `{text,scientificName,source:"ai_original",version:1}`。
- 错误保持 `{error:code}`，HTTP 400/401/413/415/429/502/503；服务端超时增加 504/provider_timeout，Android 按网络失败处理。问题最多 300 字、历史最多 10 条/6000 字，问答 body 16 KiB，自然表达/会话 body 1 KiB；输出 max_tokens 分别为 512/300。代理将 provider 截止收紧为问答 19 秒 / 自然表达 14 秒，每次计数事务等待最多 2 秒，给 Android 的 25/20 秒读取截止留余量。数据库超时后事务仍可能保守地完成扣额，但不会继续调用模型或回退到无保护模式。
- 沿用原 protocol.mjs 的白名单、提示词与内容检查，不上传照片、坐标、观察备注或已审核文化原文；不修改问答/自然表达 UI。

```powershell
node --test services/cloudbase/tests/*.test.mjs services/nature-proxy/*.test.mjs
cd services/cloudbase/wildflower-ai
npm ci --omit=dev --ignore-scripts
cd ../../..
python services/cloudbase/scripts/package.py
```

部署配置 `cloudbaserc.json` 已填入本环境 ID；通用示例 `cloudbaserc.example.json` 继续保留。两者只包含新函数的非敏感设置，不包含旧 health 函数，也不包含任何真实密钥。配置不会自动继承 `wildflower-health` 的环境变量，需要在新函数控制台单独设置；无需将任何 Key 发到聊天中。
