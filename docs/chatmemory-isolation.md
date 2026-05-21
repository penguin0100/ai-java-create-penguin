# LangChain4j 多用户 ChatMemory 串扰问题排查

## 一、现象

上线后用户反馈了一个很诡异的问题：

- 用户 A 创建了一个博客应用，正在和 AI 讨论文章布局
- 用户 B 新创建了一个商城应用，第一次和 AI 对话
- 结果 AI 给用户 B 回复的内容里，竟然出现了用户 A 的博客布局要求

更严重的是，用户 B 的对话里能看到用户 A 之前和 AI 的完整聊天记录。

## 二、排查过程

### 第一步：确认不是前端问题

F12 看了前端请求，每次 `/chat/gen/code` 都传了正确的 `appId`，前端没问题。

### 第二步：看后端日志

两个用户的 `appId` 不同，但 AI 模型返回的内容却交叉了。说明问题出在 AI 服务实例层面，不是参数传错了。

### 第三步：找到根因

检查创建 AI 服务实例的代码，发现 `StreamingChatModel` 被定义成了**单例**：

```java
// 问题代码
@Bean
public StreamingChatModel streamingChatModel() {
    return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .build();
}
```

`StreamingChatModel` 是一个有状态的流式 HTTP 客户端。当它被 Spring 管理为单例（整个应用只有一个对象），所有用户请求都共享这一个对象。内部维护的流式响应处理器（`StreamingChatResponseHandler`）就被后来的请求覆盖了。

同时，ChatMemory 的 `id` 设置也有问题。老代码里所有请求用的是同一个 memoryId，导致 Redis 里的对话记忆被多个用户混合写入。

## 三、核心问题

两个东西不隔离：

1. **AI 模型对象不隔离** — `StreamingChatModel` 是单例，多用户共享一个实例
2. **对话记忆不隔离** — ChatMemory 的 ID 相同，Redis 里混在了一起

```
用户A请求 → 单例 StreamingChatModel ─┐
                                     │ 内部状态被覆盖
用户B请求 → 单例 StreamingChatModel ─┘
                                     ↓
                            同一个 ChatMemory ID
                                     ↓
                                 Redis
                        (两个用户的记忆混在一起)
```

## 四、解决方案

### 4.1 对话记忆隔离：每个 appId 独立的 ChatMemory

核心思路：**每个 appId 一把锁，各开各的门**。

```java
// AiCodeGeneratorServiceFactory.java
private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
    // 用 appId 作为记忆的唯一标识
    MessageWindowChatMemory chatMemory = MessageWindowChatMemory
            .builder()
            .id(appId)                              // key: 不同 appId 在 Redis 里就是不同的 key
            .chatMemoryStore(redisChatMemoryStore)   // 存储到 Redis，重启不丢失
            .maxMessages(20)                         // 只保留最近 20 条，控制上下文长度
            .build();

    // 从 MySQL 加载历史对话到记忆里（比如用户离开页面再回来）
    chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);

    // 创建 AI 服务时，绑定这个独立的 chatMemory
    return AiServices.builder(AiCodeGeneratorService.class)
            .streamingChatModel(streamingChatModel)
            .chatMemoryProvider(memoryId -> chatMemory)  // memoryId 参数就是方法上 @MemoryId 的值
            .build();
}
```

`@MemoryId` 注解在接口方法上标注哪个参数是记忆 ID：

```java
// AiCodeGeneratorService.java
TokenStream generateVueProjectCodeStream(
    @MemoryId long appId,           // 这个既是记忆ID，也是传给 chatMemoryProvider 的参数
    @UserMessage String userMessage
);
```

这样 LangChain4j 内部在每次调用时：

1. 拿到 `@MemoryId` 的值（就是 `appId`）
2. 调用 `chatMemoryProvider` 获取对应该 appId 的 ChatMemory
3. 从 ChatMemory 里取出历史对话拼到 prompt 里
4. AI 就能"记住"这个应用专属的对话上下文

### 4.2 模型实例隔离：StreamingChatModel 改多例

```java
@Bean
@Scope("prototype")  // 从单例改成多例
public StreamingChatModel streamingChatModelPrototype() {
    return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .build();
}
```

然后在工厂类里每次获取新实例：

```java
// 通过 SpringContextUtil 手动获取 prototype Bean
StreamingChatModel streamingChatModel = SpringContextUtil.getBean(
    "streamingChatModelPrototype", StreamingChatModel.class
);
```

### 4.3 服务实例缓存：避免重复创建

每来一次请求都创建新的服务实例太浪费，用 Caffeine 本地缓存把实例缓存起来：

```java
private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
        .maximumSize(1000)                           // 最多缓存 1000 个
        .expireAfterWrite(Duration.ofMinutes(30))    // 写入后 30 分钟过期
        .expireAfterAccess(Duration.ofMinutes(10))   // 10 分钟没人用就回收
        .build();

public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
    String cacheKey = appId + "_" + codeGenType.getValue();
    return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
}
```

缓存键是 `appId + 代码类型`，确保不同应用、不同生成类型都有独立的实例。

## 五、隔离架构总览

```
请求1 (appId=100, 博客) ──→ serviceCache.get("100_HTML") 
                                     ↓ 缓存没有
                           createAiCodeGeneratorService(100, HTML)
                                     ↓
                           chatMemory.id = 100  ──→ Redis key: "100"
                           streamingChatModel 实例 #1
                                     ↓
                         返回 AiCodeGeneratorService 实例 #1

请求2 (appId=200, 商城) ──→ serviceCache.get("200_HTML")
                                     ↓ 缓存没有
                           createAiCodeGeneratorService(200, HTML)
                                     ↓
                           chatMemory.id = 200  ──→ Redis key: "200"
                           streamingChatModel 实例 #2
                                     ↓
                         返回 AiCodeGeneratorService 实例 #2

请求3 (appId=100, 继续聊) ──→ serviceCache.get("100_HTML")
                                     ↓ 缓存命中
                         直接返回 实例 #1  ──→ Redis key: "100" (记忆还在)
```

## 六、历史对话的持久化

ChatMemory 存在 Redis 里是临时的（有过期时间），持久化存储还在 MySQL：

```
用户发消息
    ↓
存入 MySQL (chat_history 表，按 appId 分)  ← 持久化
    ↓
更新 Redis ChatMemory (key=appId)          ← 热缓存
    ↓
下次加载时，从 MySQL 读到 Redis ChatMemory
```

加载历史的代码：

```java
// ChatHistoryServiceImpl.java
public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
    // 从 MySQL 查出该 appId 下最近的 N 条历史
    List<ChatHistory> historyList = this.list(queryWrapper);
    // 先清空当前记忆，避免重复
    chatMemory.clear();
    // 按时间顺序，一条条加进去
    for (ChatHistory history : historyList) {
        if (history 是用户消息) {
            chatMemory.add(UserMessage.from(history.getMessage()));
        } else if (history 是 AI 消息) {
            chatMemory.add(AiMessage.from(history.getMessage()));
        }
    }
}
```

这样即使用户关掉浏览器第二天再来，AI 依然记得昨天的对话内容。

## 七、总结

多用户 ChatMemory 串扰的本质原因是 **两个"共享"没做好隔离**：

| 问题         | 原因                      | 解决                                  |
| -------------- | --------------------------- | --------------------------------------- |
| 对话记忆串扰 | ChatMemory 用同一个 ID    | 每个 appId 一个独立的 ChatMemory ID   |
| 响应内容串流 | StreamingChatModel 是单例 | 改成 `@Scope("prototype")` 多例                            |
| 实例重复创建 | 每次请求新建太浪费        | Caffeine 本地缓存，按 appId+类型 缓存 |