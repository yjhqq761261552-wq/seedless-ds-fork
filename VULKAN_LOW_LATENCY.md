> 历史说明：本文件保留旧版本记录。1.0.9 的渲染器选择、滤镜范围和低延迟行为以 [当前说明](RENDERER_1.0.9.md) 为准。

> 历史记录：以下为 1.0.7 的实现与当时验证结果。1.0.8 新增 Vulkan FXAA、共享帧、事件等待及音频/存档修复；当前功能和测试范围以 [1.0.8 优化与验证](OPTIMIZATION_1.0.8.md) 为准。

# SeedlessDS 1.0.7：Vulkan 低延迟显示

本版基于已合并官方 1.0.1 更新的自制 **1.0.6-upstream**，版本名 `1.0.7-vulkan`、版本码 `117`，包名仍是 `com.seedlessds.app.debug`，与此前自制版使用同一签名，可以覆盖升级。

## 使用

关闭游戏后，在 **设置 → 视频 → Vulkan 低延迟显示（实验性）** 开启，下次打开游戏生效。默认关闭，需要 Android 7 / API 24 及兼容驱动。全局配置和每游戏覆盖均支持持久化。

选择内置“无滤镜（None）”或“线性（Linear）”；游戏运行时可在视频设置查看实际显示后端。FXAA、SMAA 等现有 GLSL 滤镜，以及修改过的同名 None/Linear 资源，会使用完整 OpenGL 路径。连接外屏且使用非零延迟校准、诊断会话或驱动失败时也会回退，保留既有功能。一次运行选用 OpenGL 后保持该后端，重开游戏重新选择，避免 EGL 和 Vulkan 争用同一个窗口。

**这是软件渲染画面的 Vulkan 显示后端。DS 3D 核心仍是原有软件渲染器，不是 melonDS 的 Vulkan 硬件 3D 渲染器。** 2 倍/更高 3D、音频、回溯的模拟开销仍然存在。是否降低操作到显示的延迟，取决于驱动、显示合成器和游戏负载，不能用后端名称或速率百分比证明。

## 参考来源与移植范围

检查了 [WatermelonDS 0.8.0.rc3 发布说明](https://github.com/SapphireRhodonite/WatermelonDS/releases/tag/0.8.0.rc3) 及应用标签提交 `9bd6a0db59f944cda961e1df7f6e692eb6d4e65b`，重点包括：

- [FrameQueue.cpp](https://github.com/SapphireRhodonite/WatermelonDS/blob/9bd6a0db59f944cda961e1df7f6e692eb6d4e65b/app/src/main/cpp/renderer/FrameQueue.cpp)：限制队列积压、完成代次协调。
- [VulkanSurfacePresenter.cpp](https://github.com/SapphireRhodonite/WatermelonDS/blob/9bd6a0db59f944cda961e1df7f6e692eb6d4e65b/app/src/main/cpp/renderer/VulkanSurfacePresenter.cpp)：WSI 提交、旋转、GPU 同步及显示策略。
- [MelonInstance.cpp](https://github.com/SapphireRhodonite/WatermelonDS/blob/9bd6a0db59f944cda961e1df7f6e692eb6d4e65b/app/src/main/cpp/MelonInstance.cpp)：低延迟模式与前后端协调。

WatermelonDS 的管线依赖 melonDS 渲染器及 GPU 输出同步，不能直接接入 SeedlessDS 的 DraStic 重构核心。本版参考限制积压、尽晚取帧及每显示器提交的思路，实现独立的 CPU 画面上传与 Vulkan 显示。没有引入其完整核心或宣称达到相同延迟。GPL-3.0 和来源声明保留在 APK 的许可证资源中。

## 实现与修正

- `video_snapshot.c` 在现有翻页锁内发布帧号及对应页的尺度、位深、两屏模式；消费者复制最近完成页到自己持有的缓冲区。不会将正在写入的核心页面直接交给异步 GPU。RGB565 转 RGBA 和 RGBA 原样复制均有主机测试。
- Vulkan 初始化动态加载 `libvulkan.so`，不新增旧 Android 必须加载的 Vulkan 依赖。每个显示线程最多一个 GPU 提交正在执行；先检查提交完成，再尝试以零超时取得交换链图像，成功后才复制最新画面。GPU 等待不放进核心翻页锁。
- 优先 MAILBOX，其后 IMMEDIATE、FIFO_RELAXED、FIFO，按驱动能力选择。MAILBOX 会替换过时的待显示画面；其他模式的流畅度、撕裂与延迟行为不同。交换链图像数受驱动最小值约束，“一个 GPU 提交”不表示只有一张交换链图像或系统合成器完全没有排队。
- 复用持久映射的上传缓冲和图集，支持两屏不同实际分辨率、PIP 混合与交换顺序。采样限制在每屏的半像素边界，避免线性插值读取另一屏。沿用原屏幕比例、整数缩放、布局和触控矩形计算。
- Android 表面旋转支持 0/90/180/270 度；设置对应 preTransform 和缓冲尺寸，并定期检查没有尺寸变化的 180 度切换。镜像等不支持的变换回退 OpenGL。
- 显示完成信号量按交换链图像持有，避免仅等 GPU 提交 fence 就错误复用仍被显示系统使用的信号量。尺寸变化时暂存旧交换链及信号量，等新交换链一次显示的 acquire 依赖完成后再回收；连续无法回收时有数量上限并回退。遵循 [Khronos 信号量复用说明](https://docs.vulkan.org/guide/latest/swapchain_semaphore_reuse.html) 与 [交换链重建示例](https://docs.vulkan.org/samples/latest/samples/api/swapchain_recreation/README.html)。
- 启用模式时主屏、外屏用 RGBA8888 窗口，OpenGL 回退匹配对应 EGL 格式，修复原 RGB565 窗口可能无法建立 Vulkan 交换链的问题。未启用时保留原 RGB565 路径。
- 外屏使用独立显示线程和 Vulkan 设备，避免外屏取得图像的等待阻塞主屏；没有把某个物理屏的刷新时钟当成另一个屏的完成依据。两屏可各自取得最近完成帧，不能保证不同刷新率的物理面板逐帧同时扫描。
- 暂停、窗口销毁、退出及错误路径会按顺序释放资源；先停止显示线程，再允许释放核心。既有输入邮箱、摇杆修复、后台回溯模块和软件 GPU 源码保持不变。

旋转实现同时参考 [Android Vulkan 预旋转指南](https://developer.android.com/games/optimize/vulkan-prerotation)。本版使用 Vulkan 1.0 基础同步路径，没有要求较新驱动的 present-fence 扩展。

## 验证

最终 APK 的测试、签名、原生库检查及 Nova 实机结果记录在 `tools/vulkan-validation.json` 和随包的“Vulkan使用与验证说明”。设备测试不包含毫秒级按键到显示延迟的仪器测量。

- Android 构建、69 项 JVM 回归、Android Lint。
- 8 组 Vulkan 主机检查：1–8 倍两屏像素打包、全部 65,536 种 RGB565、缓冲边界、完成页元数据、加载与无新帧、300 次发布/两个并发消费者、显示模式/图像数量、四向预旋转。运行生产像素与快照模块，核心状态为测试夹具；不代替实际 GPU 驱动验证。
- 5 组原生回溯回归，含 10,000 次环形缓冲随机操作、512 次后台工作线程复用。
- 两个 Vulkan 着色器按 Vulkan 1.0 编译，生成的 SPIR-V 与随源头文件一致。既有 110 个 GLSL 配置 / 147 组阶段配对静态编译链接通过，188 个滤镜资源对照 1.0.6 保持一致。
- APK 签名、同证书覆盖升级、ARM64 库的 APK 文件偏移及 ELF LOAD 段 16 KiB 对齐；检查三个 JNI 入口以及不直接依赖 `libvulkan.so`。

## 复现构建

使用 JDK 17、SDK 35、Build Tools 34.0.0、NDK 25.2.9519653、CMake 3.22.1，设置 `local.properties` 的 `sdk.dir` 后：

```text
gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python tools/run_vulkan_host_tests.py --ndk <SDK>/ndk/25.2.9519653
python tools/run_rewind_host_tests.py --ndk <SDK>/ndk/25.2.9519653
python tools/compile_vulkan_shaders.py --glslang <glslang可执行文件> --check
```

正常 APK 构建使用随源的 SPIR-V 头文件，不需要安装 glslang。重新生成时去掉 `--check`。原生依赖的三个固定版本源压缩包已包含；初次 Gradle 构建仍需正常取得 Android/Kotlin 依赖。Windows 回溯主机测试另需 Git for Windows 的 zlib1.dll 或显式传入 `--zlib-dll`。
