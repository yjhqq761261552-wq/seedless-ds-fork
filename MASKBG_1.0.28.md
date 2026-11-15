# 1.0.28-maskbg 改动说明（2026-11-14）

用户反馈两点：(1) 遮罩要默认在游戏画面下方；(2) 交换屏幕后两块屏横向飞出屏幕。本版两件都处理。

## 遮罩改为真背景层（仿西瓜的绘制方式）

1.0.27 的实现是 Compose/View 挖空盖层（盖在画面上方、按屏幕矩形挖洞）。本版整体替换为渲染器内绘制：

- **Vulkan**（主屏 + 外屏两个 presenter 同一套代码）：presenter 新增背景纹理。Kotlin 侧把解码后的位图（BGRA 直缓冲）与目标矩形经新 JNI `VulkanNative.setBackground` 传入；presenter 在帧命令里先上传（在 fence 等待之后重录命令，替换安全），再以普通采样在 render pass 内**先于双屏绘制**，采样器独立线性过滤。背景变化会强制重录一帧，暂停时也能更新。
- **OpenGL**（主屏 DsRenderer + 外屏 ExternalThread）：复用已有的 `DirectBlit`（自带状态保存/恢复），新 `GlMaskBackground` 把位图上传为纹理，在每帧清屏之后、画面合成之前按目标矩形画出。
- 槽位规则不变（双屏同显→单屏遮罩；单屏显示→所显示那块 DS 屏的遮罩，交换屏幕时跟随内容），由渲染线程按渲染器自身矩形判定（`ScreenMasks.slotFor`）；`MaskBackgrounds` 按 槽位|模式|文件指纹|表面尺寸 做键缓存，只在变化时重算。
- 删除：EmulatorScreen 的 ScreenMaskOverlay、ExternalDisplay 的 MaskView、`ScreenMasks.buildOverlay`（挖空合成不再需要）。

方向约定（真机需确认）：Vulkan 按与 DS 屏相同的 rect/uv 约定绘制，位图首行为图顶；GL 侧 GLUtils 上传的位图顶行在 v=1，DirectBlit 用 flipY=false。若某条路径上下颠倒，改对应一处即可。

## 交换屏幕根因与修复（用户截图实测像素反推确认）

用户实际布局：基础布局为左右并排（rawRects 的 else 分支，左右半屏），再用布局编辑器的每屏变换（上屏 scale≈1.446/offX≈+0.355/offY≈−0.225；下屏 scale≈1.162/offX≈−0.307/offY≈+0.096）把两块屏叠成上下排列。旧代码交换的是**变换前的原始矩形**：上屏内容改用右半屏原位＋上屏变换 → 落点 (1096, 216)，恰好右移自身宽度 927px；下屏内容改用左半屏原位＋下屏变换 → 落点 (−497, 942)，恰好左移自身宽度 745px。与用户两张截图（正常态/交换态）的实测像素逐项吻合（八个数全中），即用户所说「交换变成了更换布局」：位置被换了，内容没在原位互换。与遮罩无关（未设遮罩同样复现）。

修复（DsGlView.calculateGeometry 双屏同面分支）：先各算各的最终显示矩形（原始矩形＋本屏变换），交换时只交换**最终矩形**——两个显示框的位置/大小不动，上屏内容进下框、下屏内容进上框。topScreenRect/bottomScreenRect 与 topPx/botPx 随内容走，触摸映射、PiP、Vulkan 矩形与遮罩选槽的既有「跟随内容」逻辑因此一致。设置页预览（singlePreviewRects）与全屏编辑器（singleEditorScreens）的镜像逻辑同步去掉原始矩形交换，只保留内容标签交换。变换相同或上下基础布局时新旧行为一致；单屏显示分支（dual）不动。
