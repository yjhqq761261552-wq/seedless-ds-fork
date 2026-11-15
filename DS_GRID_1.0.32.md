# RP Duo DS Grid · 1.0.32

在设置的屏幕页选择 Vulkan 渲染器，再在图像滤镜中选择「RP Duo DS Grid · 饱满颗粒」。上下屏布局分别设为撑满各自屏幕。版本为 1.0.32-dsgrid，versionCode 142，包名 com.seedlessds.app.debug，与提供的 1.0.31 APK 使用相同签名，可覆盖安装。

## 双屏计算

| 显示屏 | 满屏输出 | 相对 DS 256×192 的倍率 |
|---|---|---|
| RP Duo 上屏 | 1920×1080 | 横向 7.5×，纵向 5.625× |
| RP Duo 下屏 | 1280×960 | 横纵各 5× |
| RG DS 参考 | 640×480 | 横纵各 2.5× |

Vulkan 每次绘制传入该画面的实际像素矩形。网格在面板像素坐标中吸附，每条原生 DS 边界对应一条一像素主线与一像素软边，横纵分别计算。主线线性光系数为 0.42，软边为 0.72，亮度半补偿为 0.5。高内部渲染分辨率的细节保留在 DS 原生网格内部。

面积重建使用硬件线性采样。普通放大每输出像素一次纹理读取。RP Duo 双屏满屏的 1×–8×内部渲染中，遇到缩小时按覆盖范围细分，最多四次读取，覆盖积分与独立参考一致。小画中画在每原生像素不足 2.5 显示像素时渐隐网格，2 倍及以下关闭网格。每输出像素覆盖超过两源纹素时采用四采样近似。

实现整体替换旧颗粒路径。设置标识 rpduo_grain、编号 17 对应新版算法，菜单直接显示新版名称。

## 来源

原始 shader 与 MIT 完整许可保存在 third-party-shaders/SuperDrastic。APK 的许可证页包含完整版权许可。[SuperDrastic 固定版本](https://github.com/JorreFog/SuperDrastic/blob/930c4c5bb3e904557cd8cc42a01a4e27834f5ba1/shaders/ds-grid.frag)

## 已完成的验证

- APK 构建、签名核对、包名及版本核对通过。实读 APK 中的 librecon.so，顶点和片元 SPIR-V 均与随源生成头逐字节一致。
- 桌面 NVIDIA RTX 5070 Ti Vulkan 离屏渲染通过 9 组检查，涵盖两种满屏分辨率、1×/2×/8×内部渲染、分数位置、四种旋转和 RG DS 参考尺寸。对照独立面积积分及边界枚举，8 位色通道最大误差为 1，RMSE 为 0.131–0.161。
- 生产 filter_math.glsl 的主机数学检查通过，包含 7462 次网格检查、416 次覆盖比较、520 次 RP Duo 覆盖比较。组装检查通过 1566 次面积比较及上下屏非等比布局检查。
- Vulkan 主机检查通过，包含 1×–8×打包、RGB565 全值域、并发快照、采样器策略与旋转。
- 应用单测 113 项中 112 项通过。现有 ScreenMasksRobolectricTest 在 Windows 的遮罩文件删除断言失败，相关应用代码及测试源码与输入包一致。静态检查 0 错误、41 警告。
- RP Duo 的 Android Vulkan 驱动、实际双屏观感及帧率检查待完成，当前 ADB 设备列表为空。

## 构建与复核

工具链沿用项目 README 中的 Android SDK 35、NDK 25.2.9519653、CMake 3.22.1、JDK 17、Gradle 8.11.1。设置本机 local.properties 中的 sdk.dir 后，运行 gradlew.bat :app:assembleDebug。

改动 shader 后使用 tools/compile_vulkan_shaders.py 重新生成头文件，并使用 --check 核对。复核脚本包括 tools/check_grain_grid.py、tools/run_filter_math_tests.py、tools/run_vulkan_host_tests.py。

源包中的 libarchive 构建支持文件已从随包且固定哈希的 libarchive-3.8.9.tar.xz 补齐。运行应用单测时，需要把 app/build.gradle.kts 中原有的 hatch-egress-proxy 测试代理按本机网络配置调整。本次验证通过临时 Gradle init 脚本清除该测试代理，并使用 Maven Central 获取 Robolectric 运行依赖。

APK SHA-256

`34b8be67b63827d91c091e1eaac26d76b5e6fd5b26866eda645077ae4c1d3570`
