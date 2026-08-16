# shiromi

> 闲着没事 vibe coding 的项目

基于 Compose Multiplatform 的洛谷（Luogu）AI 助手桌面客户端：流式 AI 对话、洛谷题目检索、OI 教练（coach）模式与每日推荐。

- **对话** — DeepSeek 流式补全，支持思考过程、工具调用与 Markdown/TeX 渲染
- **Coach 模式** — 基于学生画像的个性化指导与题目推荐
- **题目检索** — 洛谷题目详情、难度计算与标签解析（含本地缓存）

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `composeApp` | Compose Multiplatform 桌面客户端（项目主体） |
| `lib/luogu-protocol` | 洛谷协议层：Luogu API 客户端、模型、缓存与 coach 领域逻辑 |

DeepSeek 客户端与工具调用 DSL 由外部库
[deepseek-helper](https://github.com/HatoYuze/deepseek-helper)（`io.github.hatoyuze:deepseek-helper`）提供。

## 构建与运行

使用 `./gradlew`（CI 使用 JDK 17）：

```bash
./gradlew build            # 编译 + 测试（CI 门禁）
./gradlew :composeApp:run  # 本地启动桌面客户端
```

> 注：`composeApp` 内嵌浏览器（wvbridge fork，`lib/wvbridge`）用于洛谷浏览器登录。桌面端
> 需要对应平台的原生 WebView 引擎：Windows 需 [WebView2 Runtime](https://developer.microsoft.com/microsoft-edge/webview2/)
> （Win11 自带），Linux 需 `libwebkit2gtk-4.1-0`，macOS 使用系统 WKWebView。Linux 上从源码
> 构建还需 `libwebkit2gtk-4.1-dev libgtk-3-dev cmake`；若本机缺少这些工具链，可用
> `-Pwvbridge.skipNative=true` 跳过原生库构建（仅编译，登录功能不可用）。

## 洛谷登录

- **浏览器登录（推荐）**：设置页 → Luogu API →「浏览器登录」，在应用内打开洛谷登录页，
  登录成功后自动提取会话 Cookie（含 HttpOnly 的 `__client_id`）并真实 API 验证后写入配置，
  同时自动补全 UID。
- **手动粘贴**：从浏览器 DevTools 复制完整 Cookie 粘贴到设置页（兜底路径，始终可用）。

## 运行参数

桌面客户端支持以下 JVM 系统属性：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `luogu-gui.logs.dir` | 平台用户数据目录 | 日志目录覆盖（Windows 默认 `%APPDATA%/LuoguHelper/logs`，Linux 默认 `$XDG_DATA_HOME`（缺省 `~/.local/share`）下的 `luogu-gui/logs`，macOS 默认 `~/Library/Application Support/LuoguHelper/logs`） |
| `luogu-gui.logs.maxBytes` | `5242880` (5 MB) | 单个日志文件轮转大小（保留 `app.log` 与 `app.log.1`） |
| `luogu-gui.logs.maxBodyBytes` | `8192` | HTTP 请求/响应 body 写入日志前的截断长度 |
| `luogu-gui.logs.captureAssistantMessages` | `true` | 是否记录 assistant 消息原始数据（content/segments，用于渲染对比） |
| `luogu-gui.sessions.max` | `20` | 会话 LRU 缓存上限，超出驱逐最久未访问会话 |

日志采用 JSON Lines 明文格式（敏感字段已脱敏），可在设置页的 Logs 界面筛选、刷新、清空或打开日志目录。

用户数据（配置 `api_setting.toml` / `agent_prompt.toml`、`chat.db`、图片缓存）位于 `~/.luogu-gui/`。
