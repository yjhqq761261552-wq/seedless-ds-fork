package com.seedlessds.app.emu

/** A separate presenter/device keeps external display acquisition and refresh
 * waits off the main display thread. No live core page crosses either thread. */
internal class VulkanExternalThread(private val suspended: () -> Boolean) : Thread("DsVulkanExternal") {
    private val lock = Object()
    @Volatile private var quit = false
    @Volatile var failed = false
        private set
    private var handle = 0L
    private var generation = -1
    private var filter = ""
    private var lowLatency = false

    fun terminate() {
        synchronized(lock) { quit = true; lock.notifyAll() }
        VulkanNative.wake(); ExternalSurface.wakeWaiters()
        if (currentThread() !== this) join()
    }

    private fun releasePresenter() {
        if (handle != 0L) VulkanNative.destroy(handle)
        handle = 0L
        generation = -1
    }

    override fun run() {
        try {
            val geometry = FloatArray(10)
            while (!quit) {
                val snap = ExternalSurface.snapshot()
                if (suspended() || snap == null || !ExternalSurface.attached) {
                    releasePresenter()
                    if (!quit) ExternalSurface.awaitChange()
                    continue
                }
                val config = SettingsRepo.current
                val mode = RendererPolicy.vulkanFilterMode(config.filter)
                if (handle == 0L || generation != snap.generation || config.filter != filter || config.vulkanLowLatency != lowLatency) {
                    releasePresenter()
                    handle = VulkanNative.create(snap.surface, snap.width, snap.height,
                        SettingsRepo.coreScale.coerceIn(2, 8), mode, true, config.vulkanLowLatency)
                    generation = snap.generation
                    filter = config.filter
                    lowLatency = config.vulkanLowLatency
                    if (handle == 0L) {
                        if (snap.surface.isValid && ExternalSurface.snapshot()?.generation == snap.generation) {
                            failed = true
                            return
                        }
                        continue
                    }
                }
                geometry.fill(0f)
                val rect = ExternalLayout.rect(snap.width, snap.height)
                val screen = ScreenGeometry.externalScreen(config.extDisplayScreen, config.swapScreens)
                rect.copyInto(geometry, screen * 4)
                geometry[8 + screen] = 1f
                if (VulkanNative.draw(handle, geometry, false) < 0) {
                    if (snap.surface.isValid && ExternalSurface.snapshot()?.generation == snap.generation) {
                        failed = true
                        return
                    }
                    releasePresenter()
                }
                if (!quit) VulkanNative.awaitFrame(handle)
            }
        } catch (error: Exception) {
            com.seedlessds.app.AppLog.e("SeedlessVulkan", "External presenter failed", error)
            failed = true
        } finally {
            releasePresenter()
        }
    }
}
