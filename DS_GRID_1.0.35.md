# RP Duo 柔和颗粒 · 1.0.35

版本为 1.0.35-softgrain-plus，versionCode 145。设置 → 屏幕 → Vulkan → 图像滤镜中提供两档。

| 滤镜 | 设置标识与编号 | 颗粒深度（横/纵） |
|---|---|---|
| RP Duo DS Grid · 柔和颗粒 | rpduo_grain，17 | 0.22 / 0.18 |
| RP Duo DS Grid · 柔和颗粒＋ | rpduo_grain_plus，18 | 0.30 / 0.24 |

柔和颗粒对应用户在 1.0.34 上实机认可的效果。柔和颗粒＋在同一轮廓上增强约三成颗粒对比。滤镜同时作用于上下屏，每次绘制分别计算实际尺寸与相位。

## 实机问题与处理

连接的设备型号为 Retroid Pocket Duo。上屏面板 1920×1080，当前游戏保留 4∶3 比例，游戏区域实际为 1440×1080，横纵倍率均为 5.625。下屏为 1280×960，横纵倍率均为 5。此前硬网格在上屏形成 5/6 像素交替间距，原版深度使屋顶、草地和道路出现强烈切块感。

新版的颜色重建继续使用 SuperDrastic ds-grid 的面积采样方法。颗粒轮廓采用连续的低对比余弦孔径，并按每个显示像素覆盖范围作解析积分。sinc 衰减项控制分数缩放时的采样变化，双轴颗粒保持 DS 原生 256×192 密度。半补偿亮度在近似线性光空间处理。

面积采样覆盖 RP Duo 满屏或当前保比例布局的 1×–8×内部渲染，每个输出像素使用 1、2 或 4 次硬件线性采样。小画中画的颗粒在 2–2.5 倍范围渐隐，覆盖超过两源纹素的缩小使用四采样近似。

## 验证

- 在连接的 RP Duo 上覆盖安装，分别启用两档，读取上下屏截图核对。完成后恢复柔和颗粒，游戏恢复至调试前保存的场景。
- 当前场景另存于原本空的存档位 2，便于继续比较两档效果。
- 两档各 12 组桌面 Vulkan 离屏渲染检查通过，共 24 组，涵盖上下屏分辨率、上屏当前 1440×1080 区域、1×/2×/8×内部渲染、分数位置、旋转与小画中画。对照独立面积积分，8 位色通道最大误差为 1。
- 29 项渲染器、滤镜持久化、Vulkan 策略相关应用测试通过。静态检查为 0 错误、41 警告。
- 颗粒组装检查通过 36 次轮廓面积积分比较、1566 次颜色面积比较、双档强度及分数位置检查。生产滤镜数学检查与 Vulkan 主机并发/像素打包检查通过。
- 实读 APK 内 librecon.so，顶点和片元 SPIR-V 与随源生成头逐字节一致，APK 签名与用户提供的 1.0.31 包一致。
- 本轮执行了相关应用测试。此前完整应用测试的 113 项中，原有 ScreenMasksRobolectricTest 在 Windows 遮罩文件删除断言处失败，记录见 DS_GRID_1.0.32.md。
- 当前实机核对覆盖已打开游戏的场景与双屏截图。长时间游戏帧率测试待完成。

## 来源与构建

SuperDrastic 原始 shader、固定提交与 MIT 许可保存在 third-party-shaders/SuperDrastic，完整许可也进入 APK。柔和颗粒的连续轮廓依据本轮设备截图和用户反馈调整。[SuperDrastic 固定版本](https://github.com/JorreFog/SuperDrastic/blob/930c4c5bb3e904557cd8cc42a01a4e27834f5ba1/shaders/ds-grid.frag)

工具链沿用项目 README。修改 shader 后运行 tools/compile_vulkan_shaders.py 重新生成头文件，再运行 --check 核对。复核入口包括 tools/check_grain_grid.py、tools/run_filter_math_tests.py、tools/run_vulkan_host_tests.py。

源包中的 libarchive 构建支持文件已从随包、固定哈希的 libarchive-3.8.9.tar.xz 补齐。

APK SHA-256

`2f0462517c6cae315ce12b751bde1f762dbda0e427b99c53b0f586b1fd98c46f`
