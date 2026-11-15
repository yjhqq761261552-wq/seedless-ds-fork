# 1.0.29-maskflip 改动说明（2026-11-14）

用户反馈 1.0.28：遮罩上传后上下反了。

## 根因（实读代码确认）

只错在 OpenGL 路径。`GlMaskBackground` 上传位图用 `GLUtils.texImage2D`：Android 位图首行（图顶）落在纹理 v=0。而绘制时误用了 `DirectBlit` 的 flipY=false 坐标集——那是给 FBO 纹理用的（FBO 内存首行是图像底部，故该集把 v=0 放在矩形下边、v=1 放在上边）。两者叠加：矩形上边采样到 v=1 = 位图底行，图被上下颠倒。1.0.28 交付说明里预告的「若某条路径上下颠倒，改对应一处」即此。

Vulkan 路径实读复核：presenter 顶点着色器对背景与游戏画面用同一套 rect/uv 约定，背景图经 vkCmdCopyBufferToImage 按位图行序（顶行在前）上传、uv=(0,0,1,1) 采样，片段着色器普通采样分支无翻转——与游戏画面同向，本就正确，本版未动。

## 修复

`GlMaskBackground.draw` 改 `flipY = true`（v=0 在矩形上边，与位图顶行对齐），注释同步改正。该类同时服务主屏 DsRenderer 与外屏 ExternalThread，一处修复两处生效。DirectBlit 本身与其它调用方（PiP/ring 的 FBO 绘制）未动。

## 验证点（真机）

- OpenGL：设一张方向明确的遮罩图（如带文字），应与原图同向。
- Vulkan：顺手扫一眼应同样同向（代码上与游戏画面同约定，若仍反请回报，只需翻其 uv 一处）。
