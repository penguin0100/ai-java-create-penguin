# 为什么从同步阻塞改成 SSE + 虚拟线程

## 一、原来的问题

### 1. 用户等太久，页面白屏

最开始的写法很简单：用户发请求 → 后端调 AI → 等 AI 生成完 → 返回结果。

AI 生成代码需要 30~120 秒。浏览器就一直转圈，用户不知道是卡了还是在处理，体验很差。

### 2. 同时在线人多时服务崩了

Tomcat 默认只有 200 个线程。AI 处理一个请求要 30~120 秒，线程一直被占用。当第 201 个用户发请求时，就没有空闲线程了：

```
[ERROR] Task rejected
java.util.concurrent.RejectedExecutionException
```

### 3. 线程都在"干等"

线程大部分时间不是在运算，而是在等待网络上的 AI 响应（I/O 等待）。CPU 很闲（不到 10%），但线程已经满了，新请求进不来。

### 4. 多个用户共用同一个对象导致数据串流

`StreamingChatModel` 之前是单例（整个系统只有一个），多个用户同时用时：
- 用户 A 看到用户 B 的对话内容
- AI 回复串到别人那里去了

---

## 二、为什么会这样

根本原因就一个：**每个请求都占着一个线程傻等 AI 返回**。

```
用户1请求 → 线程1 等待 AI (60秒) → 返回结果 → 释放线程1
用户2请求 → 线程2 等待 AI (90秒) → 返回结果 → 释放线程2
...
用户200请求 → 线程200 等待 AI (45秒) → 返回结果 → 释放线程200
用户201请求 → 没有线程了 → 拒绝
```

这不是加线程就能解决的。线程很"贵"：每个线程默认占用 1MB 内存，Linux 上一个进程的线程数也有限制。

---

## 三、怎么解决的

### 思路 1：用 SSE 流式推送，不等全部生成完

SSE（Server-Sent Events）就是服务器主动给浏览器发数据，不用等全部完成。

类比一下：
- **原来的方式**：去食堂，等阿姨把全部菜做完，一次性端上来（等 30 分钟）
- **SSE 方式**：阿姨做一道菜就上一道，你不用等，马上能吃（体验好很多）

后端代码：

```java
@GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chatToGenCode(@RequestParam Long appId,
                                  @RequestParam String message) {
    // Flux 就像一个"水流"，AI 生成一段就流出一段
    Flux<String> contentFlux = appService.chatToGenCode(appId, message, loginUser);

    // 包装成 SSE 格式，前端就能实时收到了
    return contentFlux
            .map(chunk -> {
                Map<String, String> wrapper = Map.of("d", chunk);
                String jsonData = JSONUtil.toJsonStr(wrapper);
                return "data: " + jsonData + "\n\n";  // SSE 格式
            })
            .concatWith(Mono.just("event: done\ndata: {}\n\n"));  // 结束标志
}
```

`Flux<String>` 是 Spring 提供的"响应式流"：你把数据往里塞，订阅者（前端）就能实时收到。不用等全部塞完。

前端用浏览器自带的 `EventSource` 接收：

```javascript
const url = `/app/chat/gen/code?appId=123&message=帮我写网页`
const eventSource = new EventSource(url, { withCredentials: true })

eventSource.onmessage = (event) => {
    const { d } = JSON.parse(event.data)  // 拿到 data: 后面的内容
    messageBox.textContent += d           // 追加到聊天框，形成打字机效果
}

eventSource.addEventListener('done', () => {
    eventSource.close()  // 收到 done 事件就关掉连接
})
```

### 思路 2：用虚拟线程，不怕线程不够

**什么是虚拟线程？**

Java 21 新加的。传统的线程（叫平台线程）和操作系统线程是一对一的，很"贵"。虚拟线程和操作系统线程是多对多的，非常"便宜"。

打个比方：
- **平台线程**：一个人独占一间办公室，不管有没有在干活
- **虚拟线程**：共享办公区的工位，你不干活（比如在等网络响应）时，别人可以来坐

**怎么用的？**

```java
// 原来需要配置线程池，麻烦
executorService.submit(() -> { /* 任务 */ });

// 用虚拟线程，一句话搞定
Thread.startVirtualThread(() -> { /* 任务 */ });
```

具体场景：部署应用后需要截图，截图要打开浏览器、等页面加载、截图、上传，全是 I/O 操作。直接扔给虚拟线程：

```java
public void generateAppScreenshotAsync(Long appId, String appUrl) {
    Thread.startVirtualThread(() -> {
        String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
        // 截图完更新数据库...
    });
}
```

工作流也放在虚拟线程里跑，然后通过 Flux 把进度推给前端：

```java
public Flux<String> executeWorkflowWithFlux(String originalPrompt) {
    return Flux.create(sink -> {
        Thread.startVirtualThread(() -> {
            // 执行耗时工作流
            for (NodeOutput step : workflow.stream(...)) {
                sink.next("event: step_completed\ndata: ...\n\n");  // 每完成一步通知前端
            }
            sink.complete();
        });
    });
}
```

### 思路 3：每个请求用自己的对象，不共享

`StreamingChatModel` 从单例改成多例：

```java
@Bean
@Scope("prototype")  // 每次获取都创建新的，不会互相干扰
public StreamingChatModel streamingChatModel() {
    return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .build();
}
```

---

## 四、改造后的效果

| | 改之前 | 改之后 |
| --- | --- | --- |
| 最大并发数 | ~200 | 10000+ |
| 首字节响应 | 30~120 秒 | < 100ms（瞬间建立连接） |
| 用户体验 | 白屏等 | 打字机效果，实时看到生成 |
| 内存（100 并发时）| 100 个平台线程 ≈ 100MB | 100 个虚拟线程 + 几个载体线程 ≈ 几 MB |
| 线程等待 CPU | CPU 不到 10%，线程满了 | CPU 能跑满，线程个数不再是瓶颈 |

---

## 五、学到的经验

### 什么时候用 SSE

- 服务器往客户端单向推数据
- 数据需要分多次推送（不是一次性返回）
- 比如：AI 流式回复、文件上传进度、日志推送

### 什么时候用虚拟线程

- 任务大部分时间在等 I/O（网络请求、文件读写、数据库查询）
- 需要同时处理大量任务（成千上万）
- **不适合** CPU 密集型任务（纯计算，虚拟线程没有优势）

### 踩过的坑

1. **Flux.create 里别忘了调用 sink.complete()**，否则前端一直等，不会结束
2. **虚拟线程里尽量别用 synchronized**，会导致线程被"钉住"，用 `ReentrantLock` 代替
3. **SSE 的 EventSource 会自动重连**，不想重连要在 done 事件里手动 `.close()`
4. **`@Scope("prototype")`** 必须加，不然多个用户共用同一个 AI 模型对象，响应就串了

---

## 小结

改之前就是一句话：**每个请求占一个线程傻等着 AI 返回**。

改了之后：
- 用 **SSE** 让用户实时看到进展，不用白屏等
- 用 **虚拟线程** 让等 I/O 时不再占着线程，系统能承载更多并发
- 用 **多例模式** 让每个请求有自己独立的 AI 对象，不会串数据

