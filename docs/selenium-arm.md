# Selenium 在 Linux ARM 服务器截图踩的 3 个坑

## 背景

项目需要给用户部署的网页自动截图当封面，用了 Selenium + Chrome 无头模式。本地 Windows 开发机器上跑得好好的，但部署到 Linux ARM 服务器后就开始各种报错。

ARM 架构的服务器（比如阿里云的倚天、华为鲲鹏、树莓派、苹果 M1 做服务器）越来越常见，但 Selenium 对 ARM 的支持一直不太完善。

---

## 坑一：ChromeDriver 找不到 ARM 版

### 现象

服务器启动时直接报错：

```
WebDriverManagerException: There was an error creating WebDriver object
No binary chromium found
```

或者：

```
Cannot find any matching binary for Chrome/Chromium
```

### 原因

`WebDriverManager` 会从 Google 的 CDN 自动下载对应平台的 ChromeDriver。但 Google 官方的 ChromeDriver 只提供 x86_64（amd64）版本，**没有提供 aarch64/arm64 的版本**。

在 ARM 服务器上，`WebDriverManager` 检测不到匹配的二进制文件，就报错了。

### 解决

不要用 `WebDriverManager` 自动下载，换成手动指定 ARM 兼容的浏览器和驱动。

**方法一：用 Chromium 替代 Chrome**

ARM Linux 发行版通常自带 Chromium（Chrome 的开源版），直接用系统装的就行：

```bash
# Ubuntu/Debian ARM 服务器上
sudo apt-get update
sudo apt-get install -y chromium-browser chromium-chromedriver
```

然后在代码里指定路径：

```java
// 告诉 Selenium 浏览器和驱动的具体位置
System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver");

ChromeOptions options = new ChromeOptions();
options.setBinary("/usr/bin/chromium-browser");  // 用 Chromium 而不是 Chrome
options.addArguments("--headless");
// ... 其他配置

WebDriver driver = new ChromeDriver(options);
```

**方法二：用 WebDriverManager 的镜像**

如果一定要用 WebDriverManager，可以把下载源切换到其他镜像站（有些镜像站提供了 ARM 版本）：

```java
// 切换到淘宝镜像站
WebDriverManager.chromedriver()
    .driverRepositoryUrl(new URL("https://registry.npmmirror.com/-/binary/chromedriver"))
    .setup();
```

但这个也不能保证有 ARM 版，最稳的还是方法一。

---

## 坑二：缺少 ARM 平台的依赖库

### 现象

Chromium 装好了，但启动时报一些奇怪的错误：

```
error while loading shared libraries: libatk-1.0.so.0: cannot open shared object file
```

或者：

```
libgbm.so.1: cannot open shared object file
libnss3.so: cannot open shared object file
```

### 原因

Chrome/Chromium 的无头模式仍然依赖一些图形相关的共享库。这些库在 Windows 上系统自带，在 x86_64 的 Linux 上大家也习惯了先装好，但 ARM Linux 的 Docker 镜像通常非常精简，缺很多包。

### 解决

在 ARM Linux 上一把安装所有需要的依赖：

```bash
sudo apt-get install -y \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libgbm1 \
    libnss3 \
    libpango-1.0-0 \
    libx11-6 \
    libxcomposite1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxkbcommon0 \
    libxrandr2 \
    libxshmfence1 \
    fonts-liberation
```

如果用的是 Docker 部署，在 Dockerfile 里加上这些：

```dockerfile
FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y \
    chromium-browser chromium-chromedriver \
    # 上面那一串依赖也放这里
    libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 libgbm1 \
    libnss3 libpango-1.0-0 libx11-6 libxcomposite1 libxdamage1 \
    libxext6 libxfixes3 libxkbcommon0 libxrandr2 libxshmfence1 \
    fonts-liberation \
    && rm -rf /var/lib/apt/lists/*
```

---

## 坑三：无头模式下页面渲染异常 + 截图截不到内容

### 现象

前两个坑填完了，Chrome 能启动，也不报错了。但截图出来的图片要么是白屏，要么页面布局完全乱掉，和正常浏览器看到的完全不一样。

### 原因

ARM 服务器的 Chrome 在无头模式下，默认的窗口大小和像素比可能不对。加上服务器没有 GPU，字体渲染也不一样。

具体原因：

1. **没有设置 window-size**：Chromium 默认窗口很小（比如 800x600），很多响应式网站的移动端布局在窄窗口下完全不一样
2.  **--disable-gpu 的作用在 ARM 上不同**：ARM 平台上软件渲染的行为和 x86 有差异
3. **缺少中文字体**：如果网页有中文内容，ARM Linux 默认不会装中文字体，字变成小方块
4. **Chrome 从 115 版本开始的新无头模式**：`--headless=new` 和老的 `--headless` 行为不一样

### 解决

**设置合理的窗口大小和像素比：**

```java
ChromeOptions options = new ChromeOptions();
options.addArguments("--headless=new");     // 用新的无头模式
options.addArguments("--no-sandbox");
options.addArguments("--disable-dev-shm-usage");
options.addArguments("--window-size=1920,1080");  // 明确设置一个大的窗口
options.addArguments("--force-device-scale-factor=1");  // 禁止缩放
options.addArguments("--disable-extensions");

WebDriver driver = new ChromeDriver(options);
driver.manage().window().setSize(new Dimension(1920, 1080));
```

**安装中文字体：**

```bash
sudo apt-get install -y fonts-wqy-zenhei fonts-wqy-microhei
```

**等待页面完全加载再截图：**

```java
// 等待页面 readyState 变成 complete
WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
wait.until(webDriver ->
    ((JavascriptExecutor) webDriver)
        .executeScript("return document.readyState")
        .equals("complete")
);
// 再额外等 2~3 秒，等动态内容（React/Vue 渲染）完成
Thread.sleep(3000);
```

---

## 项目中的最终方案

把这些坑踩完后，项目的 `WebScreenshotUtils.java` 里初始化的代码：

```java
private static WebDriver initChromeDriver(int width, int height) {
    // 指定 ChromeDriver 镜像站（国内服务器网络更快）
    System.setProperty("wdm.chromeDriverMirrorUrl",
        "https://registry.npmmirror.com/binary.html?path=chromedriver");
    WebDriverManager.chromedriver().useMirror().setup();

    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless");               // 无头模式
    options.addArguments("--disable-gpu");            // 禁用 GPU（ARM 服务器必须）
    options.addArguments("--no-sandbox");             // Docker 环境需要
    options.addArguments("--disable-dev-shm-usage");  // 避免 /dev/shm 空间不足
    options.addArguments("--window-size=" + width + "," + height);
    options.addArguments("--disable-extensions");
    // 设置 User-Agent，避免被目标网站拦截
    options.addArguments("--user-agent=Mozilla/5.0 ... Chrome/91.0 ...");

    WebDriver driver = new ChromeDriver(options);
    driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    return driver;
}
```

关键点总结：

| Chrome 参数 | 作用                               |
| ------------- | ------------------------------------ |
| `--headless`            | 不显示浏览器窗口                   |
| `--disable-gpu`            | ARM 服务器无显卡，必须禁用         |
| `--no-sandbox`            | Docker/root 用户下必须加           |
| `--disable-dev-shm-usage`            | Docker 默认 `/dev/shm` 只有 64MB，不够用     |
| `--window-size`            | 明确指定窗口大小，避免截图比例异常 |