> 历史记录：1.0.11 在本版基础上新增精选 Vulkan 效果，当前列表与验证范围见 [精选滤镜说明](SHADERS_1.0.11.md)。

# SeedlessDS 1.0.10：Vulkan LCD 滤镜

直接基于自制 **1.0.9-renderer**，版本码 120，包名和签名不变，可覆盖升级。此次未连接或操作 Nova、未安装测试包；按用户要求交由用户实测。

## 新增选项

**设置 → 屏幕 → 渲染器：Vulkan → 图像滤镜**：

- LCD1x
- LCD1x + NDS Color
- zFast LCD
- zFast LCD + NDS Color

原有无滤镜、线性和 FXAA 保留；默认渲染器仍为 OpenGL。新增 LCD 选项直接走 Vulkan，主屏和副屏使用同一个模式映射，不会因为选择 LCD 而回退 OpenGL；低延迟开/关均可配置。运行中可切换滤镜，切换渲染器需先关闭游戏。

## 移植内容

来自 1.0.9 自带的 lcd1x、lcd1x+nds_color、zfast_lcd、zfast_lcd+nds_color 源文件，保留各作者和 GNU GPL v2-or-later 声明，并加入 APK 许可证资源。

LCD1x 使用原版默认 BRIGHTEN_SCANLINES=16、BRIGHTEN_LCD=4；zFast LCD 使用 BORDERMULT=14。NDS Color 保留原来的色彩矩阵、gamma=1.91 和亮度系数 0.89，在矩阵之后增加安全钳位，避免饱和蓝等输入导致负数的分数次幂产生 NaN。它不是所有颜色下与旧 OpenGL 未定义行为逐像素相同的承诺。

网格从每屏独立的 UV 图集区域映射到 **256×192 原生 DS 像素坐标**，不使用整张图集坐标或设备像素坐标。设计上保持 1–8 倍 3D、两屏不同实际分辨率、PIP、交换屏幕和副屏下的网格密度与归属。取样限制在本屏边界，PIP 保留原透明度。

LCD 使用最近邻取样；线性/FXAA 仍使用线性取样，避免 LCD 网格叠加额外模糊。所有效果为当前呈现 pass 内的分支，无额外帧队列、画面复制或离屏 pass。软件模拟核心和原 OpenGL 滤镜文件均未修改。

这是内置默认参数的移植，不读取用户修改过的同名 OpenGL 着色器参数；没有加入所有 LCD 衍生组合、natural_vision、DS Lite/DSi 色彩或亮度增强版本。

## 本轮验证范围

- APK 构建成功，**89 项 JVM 测试通过**；Lint **0 错误 / 27 警告**。
- 新增滤镜的可选列表、固定模式编号、渲染器选择、低延迟开/关、全局/每游戏持久化测试通过。
- 11 组原生主机检查通过，包含生产采样策略中 LCD 使用最近邻的验证，原有画面复制、位深、1–8 倍、并发快照和 WSI 策略回归；这些不是 GPU 图像测试。
- 顶点/片段着色器按 Vulkan 1.0 编译成功，随源 SPIR-V 与 GLSL 再编译结果一致；push constants 仍为原有 64 字节布局。
- APK 全部 assets 与源码对照一致；签名和 5 个 ARM64 库的 ELF LOAD / APK 文件偏移 16KiB 对齐通过。
- **未做真机出图、性能、驱动兼容、物理双屏或端到端延迟验证，未覆盖安装 Nova。** 源码中的 1.0.9 及更早验证记录均为历史结果，不代表新增 LCD 的真机结果。

## 自测建议

先从 LCD1x 或 zFast LCD 开始，使用整数缩放更容易观察稳定的网格；再对比 NDS Color 版本的颜色。可依次检查 1 倍/2 倍 3D、PIP、交换屏幕和外屏，并查看屏幕页的实际后端仍为 Vulkan。非整数放大下细网格可能出现摩尔纹，视觉效果和亮度请以设备实测为准。

## 复现构建

JDK17、SDK35、Build Tools34.0.0、NDK25.2.9519653、CMake3.22.1；配置 local.properties 的 sdk.dir 后：

```text
gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python tools/run_vulkan_host_tests.py --ndk <NDK目录>
python tools/compile_vulkan_shaders.py --glslang <glslang路径> --check
```

普通 APK 构建使用随源 SPIR-V，不要求安装 glslang。源码包含固定版本原生依赖的源压缩包，不含 ROM、用户存档、账号或构建缓存；初次 Android 构建仍需下载 Gradle/Android 依赖。

APK SHA-256：`37c9bf1d959bce2f82f36c874520d16b97e2130d21e3dcbaa9ea9f8b494f7619`

签名证书 SHA-256：`99e2395bd7c458a577e6bc4f4c7237a7474cb20a178fad07313c2778a7943165`
