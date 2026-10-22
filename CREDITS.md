# 致谢与来源

特别感谢 **SapphireRhodonite，以及 WatermelonDS / SeedlessDS 制作组和所有贡献者**。感谢你们公开源码、持续维护 Android 掌机与外屏体验，并为本分支提供改进基础和参考。

- [SeedlessDS](https://github.com/SapphireRhodonite/SeedlessDS)：本分支的直接上游，保留原项目名称、作者与源码许可声明。
- [WatermelonDS](https://github.com/SapphireRhodonite/WatermelonDS)：Vulkan 低延迟呈现、输入、音频、存档及外屏优化的对照参考；本轮参考版本为 [0.8.0.rc3](https://github.com/SapphireRhodonite/WatermelonDS/releases/tag/0.8.0.rc3)。本分支没有完整移植其 melonDS 核心、librashader 或所有渲染后端。
- [melonDS](https://github.com/melonDS-emu/melonDS) 的 Arisotura 与贡献者、[melonDS Android](https://github.com/rafaelvcaetano/melonDS-android) 的 rafaelvcaetano 与贡献者：感谢模拟器生态与 Android 实现经验。SeedlessDS 使用的是随源提供的 SeedlessCoreDS，具体核心来源见 [核心说明](core/README.md)。
- DraStic 原作者，包括 Exophase：保留上游对 DraStic r2.6.0.4a 及重建核心的来源声明。本分支并非 DraStic 官方版本。
- [rcheevos](https://github.com/RetroAchievements/rcheevos)、原生依赖、字体、替代 BIOS、数据库和金手指目录的各位作者。详见 [许可说明](LICENSING.md)、[原生依赖许可](core/third_party/LICENSES.md) 及 app/src/main/assets/licenses/。

## 着色器作者

### OpenGL / GLES 滤镜来源

感谢以下原算法作者、着色器作者及 DraStic 格式移植者。下表依据本仓库随附源码头部和许可证整理；组合滤镜同时继承其各组成部分的作者声明。

| 滤镜 / 家族 | 作者与贡献 | 随源依据 |
|---|---|---|
| xBR：2XBR / 4XBR / 5XBR 及 CRT 组合 | Hyllian / Jararaca | [2XBR 源码声明](app/src/main/assets/shaders/2XBR_v3.7C.dsd)，其他 xBR 文件保留各自 GPL 声明 |
| SABR v3.0 及优化组合 | Joshua Street；部分算法来自 Hyllian 的 5xBR；随源注明 Try791023 移植到 DraStic | [SABR 源码](app/src/main/assets/shaders/SABR_v3.0.dsd) |
| LCD1x、Sharp Bilinear、zFast LCD 和色彩组合的 DraStic 移植 | jdgleaver | [随包 GPL shader 声明](app/src/main/assets/licenses/GPL-shader-notices.txt) 与各 .dsd 文件 |
| LCD 网格算法 | Gigaherz（原 lcd3x） | [LCD1x 源码头部](app/src/main/assets/shaders/lcd1x.dsd) |
| Sharp Bilinear | rsn8887、Themaister | [随包声明](app/src/main/assets/licenses/GPL-shader-notices.txt) |
| zFast LCD | Greg Hogan / SoltanGris42 | [zFast LCD 源码](app/src/main/assets/shaders/zfast_lcd.dsd) |
| NDS / DS Lite / DSi Color | hunterk，Pokefan531 修改；jdgleaver 移植 | [NDS Color 源码](app/src/main/assets/shaders/nds_color.dsd) 及各色彩组合头部 |
| Natural Vision | ShadX 原作，Hyllian、Sp00kyFox 修改；jdgleaver 移植 | [Natural Vision 源码](app/src/main/assets/shaders/natural_vision.dsd) |
| Retro Tiles | Hyllian，MiZai 移植 | [Retro Tiles 源码声明](app/src/main/assets/shaders/retro-tiles.dsd) |
| HQ2X / 2xScaleHQ | guest(r) | [HQ2X 许可与固定来源](app/src/main/assets/licenses/HQ2X-GPL.txt)；此文件不是 Maxim Stepin 的另一套 hqx 实现 |
| Scale2X | Andrea Mazzoleni 原算法，Ryan A. Nunn 的 DX9 改编启发，Pete Bernert 的 GLslang 移植 | [Scale2X 来源声明](app/src/main/assets/licenses/Scale2X-GPL.txt) |
| FXAA 3.11 | NVIDIA Corporation | [BSD-3-Clause 声明](app/src/main/assets/licenses/FXAA-BSD-3-Clause.txt) |
| SMAA | Jorge Jimenez、Jose I. Echevarria、Belen Masia、Fernando Navarro、Diego Gutierrez | [SMAA 许可](app/src/main/assets/licenses/SMAA.txt) |
| Quilez 插值 | hunterk 的公有领域实现；插值公式来自 Iñigo Quílez | [Quilez 来源声明](app/src/main/assets/licenses/Quilez-public-domain.txt) |
| Scanline | Timothy Lottes | [Scanline 公有领域声明](app/src/main/assets/licenses/Scanline-public-domain.txt) |

同时感谢 Libretro / RetroArch 着色器维护者，以及将滤镜整理进上游 SeedlessDS 的贡献者。CRT-Geom 文件保留了 Libretro common-shaders 的来源链接；Dot、Cartoon、部分 CRT/Scanlinesd 和组合配方未提供足够完整的逐文件作者声明，本表不凭名称推定作者，也不将它们归为本分支原创。欢迎原作者提供可核对的来源补充。完整原始注释和许可保留在 [OpenGL 滤镜目录](app/src/main/assets/shaders/) 与 [许可证目录](app/src/main/assets/licenses/)。本致谢不替代或更改各文件的原始许可。

### 新增 Vulkan 移植

感谢 Themaister / rsn8887（Sharp Bilinear）、zadpos / Woohyun Kang（Sharp Shimmerless）、fishku（Pixel AA）、Gigaherz（LCD3x）、hunterk 与 Iñigo Quílez（Quilez 插值实现与公式）、jdgleaver（DraStic shader 移植）、Greg Hogan / SoltanGris42（zFast）、NVIDIA（FXAA），以及 Libretro 着色器维护者和其他随源列出的作者。

新增移植的原始文件、固定提交和校验清单保存在 [third-party-shaders](third-party-shaders/)，各文件原有版权与许可继续适用。来源和实现差异见 [1.0.11 说明](SHADERS_1.0.11.md)。

本分支为第三方自制修改，未获得上游官方背书。原项目和第三方成果不因本分支修改而转移著作权。Nintendo 及游戏名称归各自权利人所有。
