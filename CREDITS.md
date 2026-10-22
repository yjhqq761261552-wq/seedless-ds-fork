# 致谢与来源

特别感谢 **SapphireRhodonite，以及 WatermelonDS / SeedlessDS 制作组和所有贡献者**。感谢你们公开源码、持续维护 Android 掌机与外屏体验，并为本分支提供改进基础和参考。

- [SeedlessDS](https://github.com/SapphireRhodonite/SeedlessDS)：本分支的直接上游，保留原项目名称、作者与源码许可声明。
- [WatermelonDS](https://github.com/SapphireRhodonite/WatermelonDS)：Vulkan 低延迟呈现、输入、音频、存档及外屏优化的对照参考；本轮参考版本为 [0.8.0.rc3](https://github.com/SapphireRhodonite/WatermelonDS/releases/tag/0.8.0.rc3)。本分支没有完整移植其 melonDS 核心、librashader 或所有渲染后端。
- [melonDS](https://github.com/melonDS-emu/melonDS) 的 Arisotura 与贡献者、[melonDS Android](https://github.com/rafaelvcaetano/melonDS-android) 的 rafaelvcaetano 与贡献者：感谢模拟器生态与 Android 实现经验。SeedlessDS 使用的是随源提供的 SeedlessCoreDS，具体核心来源见 [核心说明](core/README.md)。
- DraStic 原作者，包括 Exophase：保留上游对 DraStic r2.6.0.4a 及重建核心的来源声明。本分支并非 DraStic 官方版本。
- [rcheevos](https://github.com/RetroAchievements/rcheevos)、原生依赖、字体、替代 BIOS、数据库和金手指目录的各位作者。详见 [许可说明](LICENSING.md)、[原生依赖许可](core/third_party/LICENSES.md) 及 app/src/main/assets/licenses/。

## 着色器作者

感谢 Themaister / rsn8887（Sharp Bilinear）、zadpos / Woohyun Kang（Sharp Shimmerless）、fishku（Pixel AA）、Gigaherz（LCD3x）、hunterk 与 Iñigo Quílez（Quilez 插值实现与公式）、jdgleaver（DraStic shader 移植）、Greg Hogan / SoltanGris42（zFast）、NVIDIA（FXAA），以及 Libretro 着色器维护者和其他随源列出的作者。

新增移植的原始文件、固定提交和校验清单保存在 [third-party-shaders](third-party-shaders/)，各文件原有版权与许可继续适用。来源和实现差异见 [1.0.11 说明](SHADERS_1.0.11.md)。

本分支为第三方自制修改，未获得上游官方背书。原项目和第三方成果不因本分支修改而转移著作权。Nintendo 及游戏名称归各自权利人所有。
