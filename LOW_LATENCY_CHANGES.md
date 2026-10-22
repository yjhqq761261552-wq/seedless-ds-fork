# Grok 版本历史说明（已由 1.0.2 修正）

用户提供的 Grok 源码相对原版 `1.0.1-dualscreen` 曾做以下修改；此列表仅记录历史，不是当前实现：

1. `DsSettings.extDisplayDelay` 默认值由 `1` 改为 `0`（双屏不再默认把主屏晚 1 帧）。
2. `DsGlView` / `ExtSurfaceView`：API 30+ 在触摸时调用 `requestUnbufferedDispatch`；API 26–29 在 surfaceCreated 请求无缓冲 DOWN。
3. 触摸 `ACTION_MOVE` 补上 `historical` 坐标后再提交当前点。

其中第 2 项的 API 26–29 整数重载存在兼容性和参数错误，第 3 项不适合仅保留最新状态的输入通道。当前实现、进一步优化和验证范围见 [INPUT_LATENCY_FIXES.md](INPUT_LATENCY_FIXES.md)。
