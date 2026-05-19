<p align="center">
  <h1 align="center">🐧 AI Java Create（AI 企鹅）</h1>
  <p align="center">
    <strong>AI 驱动的全栈代码生成平台</strong><br>
    自然语言描述需求 → AI 自动生成可运行的前端项目
  </p>
</p>

<p align="center">
  <a href="#-技术栈"><img src="https://img.shields.io/badge/JDK-21-orange" alt="JDK 21"></a>
  <a href="#-技术栈"><img src="https://img.shields.io/badge/Spring_Boot-3.5.13-brightgreen" alt="Spring Boot 3.5.13"></a>
  <a href="#-技术栈"><img src="https://img.shields.io/badge/Vue-3.5-4FC08D" alt="Vue 3.5"></a>
  <a href="#-技术栈"><img src="https://img.shields.io/badge/LangChain4j-1.1.0-blue" alt="LangChain4j"></a>
  <a href="#license"><img src="https://img.shields.io/badge/license-MIT-green" alt="License"></a>
</p>

---

## 📖 项目简介

**AI Java Create（AI 企鹅）** 是一个 AI 驱动的代码生成平台。用户只需用自然语言描述想要的网站，系统就能自动生成完整、可运行的代码 —— 从简单的单页 HTML，到完整的 Vue 3 + Vite 前端工程，并支持一键部署、自动截图预览和源码下载。

### 核心流程

```
用户输入需求 → AI 智能路由判断生成类型 → 流式生成代码 → 实时预览 → 一键部署 → 自动截图 → 下载源码
```

### 支持的代码生成类型

| 类型 | 说明 | 适用场景 |
|------|------|---------|
| **HTML** | 单文件 HTML（内联 CSS/JS） | 简单静态页面、个人主页、宣传页 |
| **Multi-File** | 分离的 HTML + CSS + JS | 中等复杂度交互页面 |
| **Vue Project** | 完整 Vue 3 + Vite + TypeScript 工程 | 复杂 SPA 应用、管理后台 |

---

## ✨ 核心特性

- **🧠 AI 智能代码生成** — 基于 DeepSeek + LangChain4j，支持三种复杂度等级的代码生成，从自然语言到可运行代码
- **🔄 流式实时交互** — SSE（Server-Sent Events）实时推送 AI 生成过程，支持打字机效果和工具调用可视化
- **🤖 LangGraph4j 多智能体工作流** — 串行/并行两种模式，自动完成图片收集、Prompt 增强、代码生成、质量检查、项目构建
- **🎨 设计模式优雅实践** — Facade、Strategy、Template Method、Factory Method 等模式在代码解析/保存系统中的实际应用
- **📦 一键部署与预览** — 生成后自动部署，支持 Vite 项目 npm 构建、静态资源服务、SPA 路由 fallback
- **📸 自动截图** — Selenium 无头 Chrome 自动截取页面预览图，上传腾讯云 COS
- **🔐 安全与限流** — 输入/输出双护轨（Guardrail），Redisson 分布式限流，Session-based 认证
- **⚡ 两级缓存体系** — Caffeine 本地缓存（AI Service 实例）+ Redis 分布式缓存（业务数据/Session/ChatMemory）
- **📥 源码下载** — 一键打包下载完整项目源码（自动排除 node_modules 等）
- **🖼️ 智能图片收集** — 自动从 Pexels、Undraw、DashScope 等收集配图、Logo、插画

---

## 🏗️ 技术栈

### 后端
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.13 | 应用框架 |
| JDK | 21 | 运行环境（支持虚拟线程） |
| MyBatis-Flex | 1.11.0 | ORM |
| MySQL | 8.x | 业务数据存储 |
| Redis | 6.x+ | Session / 缓存 / 限流 / ChatMemory |
| LangChain4j | 1.1.0 | AI 框架（模型调用、结构化输出、Function Calling） |
| LangGraph4j | 1.6.0-rc2 | 多智能体工作流编排 |
| DeepSeek v4 Pro | — | AI 大语言模型（OpenAI 兼容 API） |
| Redisson | 3.50.0 | 分布式锁 / 限流 |
| Selenium | 4.33.0 | 无头 Chrome 截图 |
| 腾讯云 COS | 5.6.227 | 对象存储（截图 / 图片资源） |
| 阿里云 DashScope | 2.21.1 | AI 图片生成（Logo） |
| Knife4j | 4.4.0 | API 文档 |
| Hutool | 5.8.38 | 工具集 |

### 前端
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5 | 前端框架 |
| Vite | 7.x | 构建工具 |
| TypeScript | 5.8 | 类型安全 |
| Ant Design Vue | 4.2 | UI 组件库 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.5 | 路由管理 |
| Axios | 1.14 | HTTP 客户端 |

---

## 📁 项目结构

```
ai-java-create/
├── src/main/java/org/example/aijavacreate/
│   ├── controller/          # REST 控制器（App/User/ChatHistory/StaticResource）
│   ├── service/             # 业务服务层
│   ├── ai/                  # AI 层
│   │   ├── AiCodeGeneratorService.java          # AI 代码生成服务（LangChain4j 接口）
│   │   ├── AiCodeGeneratorServiceFactory.java   # AI Service 工厂 + Caffeine 缓存
│   │   ├── AiCodeGenTypeRoutingService.java     # 智能路由服务（判断生成类型）
│   │   ├── tool/FileWriteTool.java             # 文件写入工具
│   │   ├── tool/ExitTool.java                  # 退出工具（防 AI 循环）
│   │   └── guardrail/                          # 安全护轨（输入/输出检查）
│   ├── core/                # 核心层（设计模式集中体现）
│   │   ├── parser/          # 策略模式 — 代码解析器
│   │   ├── saver/           # 模板方法模式 — 代码保存器
│   │   ├── streamhandler/   # 流式处理器
│   │   ├── builder/         # Vue 项目构建器
│   │   └── facade/          # 外观模式 — 门面
│   ├── langgraph4j/         # LangGraph4j 多智能体工作流
│   │   ├── CodeGenWorkflow.java               # 串行工作流
│   │   └── CodeGenConcurrentWorkflow.java      # 并行工作流
│   ├── config/              # 配置类（COS/CORS/Redis/ChatMemory 等）
│   ├── aop/                 # 切面（权限拦截 / 限流）
│   ├── model/               # 实体 / DTO / VO / 枚举
│   ├── mapper/              # MyBatis-Flex Mapper
│   ├── constant/            # 常量
│   ├── exception/           # 异常处理
│   └── utils/               # 工具类
├── src/main/resources/
│   ├── application.yml              # 主配置
│   ├── application-local.yml        # 本地环境配置
│   ├── application-prod.yml         # 生产环境配置
│   └── prompt/                      # AI System Prompt 文件
│       ├── codegen-html-system-prompt.txt
│       ├── codegen-multi-file-system-prompt.txt
│       ├── codegen-vue-project-system-prompt.txt
│       └── codegen-routing-system-prompt.txt
├── sql/
│   └── create_table.sql     # 数据库建表语句
├── ai-java-create-frontend/ # 前端项目（Vue 3 + Vite + Ant Design）
│   └── src/
│       ├── pages/           # 页面组件
│       ├── components/      # 通用组件
│       ├── layouts/         # 布局组件
│       ├── stores/          # Pinia 状态管理
│       ├── router/          # 路由配置
│       ├── api/             # API 接口封装
│       └── access.ts        # 路由守卫
└── pom.xml                  # Maven 依赖配置
```

---

## 🚀 快速开始

### 环境要求

- **JDK** 21+
- **Maven** 3.6+
- **MySQL** 8.0+
- **Redis** 6.0+
- **Node.js** 18+（前端开发 / Vue 项目构建）
- **Chrome** 浏览器（截图功能需要）

### 1. 克隆项目

```bash
git clone https://github.com/penguin0100/ai-java-create-penguin.git
cd ai-java-create
```

### 2. 初始化数据库

```sql
CREATE DATABASE ai_java_penguin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后执行 `sql/create_table.sql` 建表。

### 3. 配置环境

编辑 `src/main/resources/application-local.yml`，填入你的配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_java_penguin
    username: root
    password: 你的数据库密码
  data:
    redis:
      host: localhost
      port: 6379
      password: 你的Redis密码（如果没有则留空）

langchain4j:
  open-ai:
    chat-model:
      api-key: 你的DeepSeek_API_Key
    streaming-chat-model:
      api-key: 你的DeepSeek_API_Key
    reasoning-streaming-chat-model:
      api-key: 你的DeepSeek_API_Key
    routing-chat-model:
      api-key: 你的DeepSeek_API_Key

cos:
  client:
    secretId: 你的腾讯云SecretId
    secretKey: 你的腾讯云SecretKey
    region: ap-beijing
    bucket: 你的COS_Bucket名称
```

### 4. 启动后端

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

启动后访问：
- API 服务：`http://localhost:8080/api`
- API 文档（Knife4j）：`http://localhost:8080/api/doc.html`

### 5. 启动前端

```bash
cd ai-java-create-frontend
npm install
npm run dev
```

访问：`http://localhost:5173`

---

## 📡 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/user/register` | 用户注册 |
| POST | `/api/user/login` | 用户登录 |
| GET | `/api/user/get/login` | 获取当前用户信息 |
| POST | `/api/app/add` | 创建应用 |
| POST | `/api/app/update` | 更新应用 |
| POST | `/api/app/delete` | 删除应用 |
| POST | `/api/app/my/list/page/vo` | 我的应用列表 |
| GET | `/api/app/chat/gen/code` | **AI 流式代码生成（SSE）** |
| POST | `/api/app/deploy` | 部署应用 |
| GET | `/api/app/download/{appId}` | 下载源码 ZIP |
| GET | `/api/static/{deployKey}/**` | 访问部署的静态资源 |
| POST | `/api/user/admin/*` | 管理员接口 |
| POST | `/api/app/admin/*` | 管理员接口 |

---

## 🔧 关键设计

### 设计模式应用

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| **外观模式** | `core/facade/AiCodeGeneratorFacade` | 统一封装 AI 生成 + 解析 + 保存 + 流处理 |
| **策略模式** | `core/parser/CodeParser` + `core/streamhandler/` | 不同代码类型使用不同解析/流处理策略 |
| **模板方法** | `core/saver/CodeFileSaverTemplate` | 固定保存流程，子类实现具体文件写入 |
| **工厂方法** | `ai/AiCodeGeneratorServiceFactory` | 按 appId + codeGenType 创建不同配置的 AI Service |
| **AOP** | `aop/AuthInterceptor`、`aop/RateLimitAspect` | 权限校验、限流 |

### 多智能体工作流

```
START
  → ImageCollector（图片收集）
  → PromptEnhancer（Prompt 增强）
  → Router（类型判断）
  → CodeGenerator（代码生成）
  → QualityCheck（质量检查）
    → [通过] → ProjectBuilder（项目构建）→ END
    → [不通过] → CodeGenerator（重试修复）
```

并行模式支持 4 路并发图片收集（内容配图 + 插画 + 架构图 + Logo），节约 60-70% 的图片收集时间。

### 缓存体系

| 缓存 | 技术 | 用途 | 配置 |
|------|------|------|------|
| AI Service 实例 | Caffeine | Factory 创建的 AI Service | maxSize=1000, 写过期30min, 读过期10min |
| 业务数据 | Redis (Spring Cache) | 精选列表等 | 默认30min, good_app_page 5min |
| Chat Memory | Redis (ChatMemoryStore) | 对话上下文 | TTL 3600s, 窗口20条 |
| Session | Redis (Spring Session) | 用户认证 | 30天 |
| 限流 | Redis (Redisson RRateLimiter) | API 调用频率控制 | 5次/60秒/用户 |

---

## 🌐 部署

### 生产环境

切换到 `prod` 配置文件：

```bash
java -jar ai-java-create.jar --spring.profiles.active=prod
```

前端构建：

```bash
cd ai-java-create-frontend
npm run build
# 将 dist/ 部署到 Nginx 等静态服务器
```

---

## 📄 License

MIT License

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

**Made with ❤️ and AI**
